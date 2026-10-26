package org.cattail.m30lab

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/** Only selected, supported phone-side protocol operations belong behind this interface. */
interface PhoneProtocolBackend {
    val id: String
    fun isRunning(): Boolean
    fun start(target: BluetoothDevice, service: UUID, requests: Int, durationSeconds: Int,
              onFinished: (String) -> Unit)
    fun stop()
    fun resultJson(): String
    fun hasResult(): Boolean
}

/** Android framework RFCOMM via the existing HAL; no raw HCI or BlueZ dependency. */
class AndroidRfcommBackend(private val handler: Handler) : PhoneProtocolBackend {
    companion object {
        val FIXTURE_UUID: UUID = UUID.fromString("7f5f42d6-4659-4c15-9fe7-7de7a7033b02")
        const val REQUEST = "M30LAB/1 PING\n"
        const val RESPONSE = "M30LAB/1 PONG\n"
    }
    override val id = "Android Bluetooth RFCOMM service UUID"
    private val stopped = AtomicBoolean(false)
    @Volatile private var activeSocket: BluetoothSocket? = null
    @Volatile private var worker: Thread? = null
    private var generation = 0
    private var result = JSONObject().put("status", "not_tested")

    override fun isRunning() = worker?.isAlive == true

    override fun start(target: BluetoothDevice, service: UUID, requests: Int,
                       durationSeconds: Int, onFinished: (String) -> Unit) {
        require(!isRunning() && requests in 1..20 && durationSeconds in 1..30)
        val plan = PhoneRunPlan(requests, durationSeconds)
        stopped.set(false)
        generation++
        val thisRun = generation
        val started = SystemClock.elapsedRealtime()
        val deadline = started + durationSeconds * 1000L
        val operations = JSONArray()
        val runId = UUID.randomUUID().toString()
        result = JSONObject().put("schema", "m30-security-lab/mobile-connection/v1")
            .put("run_id", runId).put("clock", "android.elapsedRealtime")
            .put("execution_location", "phone").put("backend", id)
            .put("controller_owner", "Android Bluetooth service").put("mechanism", "RFCOMM connection test")
            .put("target", target.address).put("service_uuid", service.toString())
            .put("limits", JSONObject().put("requests", requests).put("duration_s", durationSeconds)
                .put("per_operation_timeout_s", 12).put("workers", 1))
            .put("status", "running").put("baseline", JSONObject.NULL).put("operations", operations)
            .put("target_existing_link_effect", "not_measured")
        worker = Thread({
            try {
                val baseline = attempt(target, service, started, plan)
                synchronized(result) { result.put("baseline", baseline) }
                if (!baseline.optBoolean("connected") || (service == FIXTURE_UUID && !baseline.optBoolean("exchange_ok"))) {
                    synchronized(result) { result.put("status", if (stopped.get()) "stopped" else "baseline_failed") }
                } else {
                    var failed = false
                    for (index in 1..requests) {
                        if (!plan.canRunNext(operations.length(), SystemClock.elapsedRealtime() - started, stopped.get())) break
                        val operation = attempt(target, service, started, plan).put("index", index)
                        synchronized(result) { operations.put(operation) }
                        if (!operation.optBoolean("connected") || (service == FIXTURE_UUID && !operation.optBoolean("exchange_ok"))) { failed = true; break }
                        val wait = minOf(1000L, deadline - SystemClock.elapsedRealtime())
                        if (wait > 0) Thread.sleep(wait)
                    }
                    synchronized(result) {
                        result.put("status", when {
                            stopped.get() -> "stopped"
                            failed -> "failed"
                            SystemClock.elapsedRealtime() >= deadline && operations.length() < requests -> "deadline"
                            else -> "completed"
                        })
                    }
                }
            } catch (_: InterruptedException) {
                synchronized(result) { result.put("status", "stopped") }
            } finally {
                try { activeSocket?.close() } catch (_: Exception) {}
                activeSocket = null
                synchronized(result) { result.put("elapsed_ms", SystemClock.elapsedRealtime() - started) }
                handler.post { if (thisRun == generation) onFinished(result.optString("status")) }
            }
        }, "m30-rfcomm-test").also { it.start() }
    }

    private fun attempt(target: BluetoothDevice, service: UUID, started: Long, plan: PhoneRunPlan): JSONObject {
        val begun = SystemClock.elapsedRealtime()
        val remaining = plan.operationTimeoutMs(begun - started)
        val operation = JSONObject()
            .put("phase", if (stopped.get()) "cancelled_before_socket" else "deadline_before_socket")
            .put("started_offset_ms", begun - started)
            .put("connected", false)
        if (remaining <= 0 || stopped.get()) return operation.put("error", "deadline/cancelled")
        var socket: BluetoothSocket? = null
        var connected = false
        val timedOut = AtomicBoolean(false)
        val timeout = Runnable { timedOut.set(true); try { socket?.close() } catch (_: Exception) {} }
        try {
            socket = target.createRfcommSocketToServiceRecord(service)
            operation.put("phase", "connect").put("socket_created_offset_ms", SystemClock.elapsedRealtime() - started)
            activeSocket = socket
            handler.postDelayed(timeout, remaining)
            socket.connect()
            connected = true
            operation.put("connected", true).put("connected_offset_ms", SystemClock.elapsedRealtime() - started)
                .put("phase", if (service == FIXTURE_UUID) "exchange" else "connected")
            if (service == FIXTURE_UUID) {
                socket.outputStream.write(REQUEST.toByteArray(StandardCharsets.US_ASCII))
                socket.outputStream.flush()
                val received = ArrayList<Byte>(64)
                while (received.size < 64) {
                    val b = socket.inputStream.read()
                    if (b < 0) break
                    received.add(b.toByte())
                    if (b == '\n'.code) break
                }
                val reply = String(received.toByteArray(), StandardCharsets.US_ASCII)
                operation.put("request", REQUEST.trimEnd()).put("response", reply.trimEnd())
                    .put("exchange_ok", reply == RESPONSE)
            }
            operation.put("phase", "completed")
        } catch (e: Exception) {
            operation.put("connected", connected).put("exchange_ok", false).put("timed_out", timedOut.get())
                .put("phase", if (timedOut.get()) "connect_timeout_or_closed" else "exception")
                .put("error", e.javaClass.simpleName).put("error_message", redactError(e.message))
        } finally {
            handler.removeCallbacks(timeout)
            try { socket?.close() } catch (_: Exception) {}
            activeSocket = null
            operation.put("timed_out", timedOut.get())
                .put("closed_offset_ms", SystemClock.elapsedRealtime() - started)
                .put("elapsed_ms", SystemClock.elapsedRealtime() - begun)
                .put("phase", if (operation.optString("phase") == "completed") "closed" else operation.optString("phase"))
        }
        return operation
    }

    private fun redactError(message: String?): String =
        (message ?: "").replace(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"), "<address>").take(256)

    override fun stop() {
        stopped.set(true)
        try { activeSocket?.close() } catch (_: Exception) {}
        worker?.interrupt()
        synchronized(result) { if (result.optString("status") == "running") result.put("status", "stopped") }
    }

    override fun resultJson(): String = synchronized(result) { result.toString(2) }
    override fun hasResult(): Boolean = synchronized(result) { result.optString("status") != "not_tested" }
}
