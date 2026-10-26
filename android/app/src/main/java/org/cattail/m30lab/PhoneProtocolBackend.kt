package org.cattail.m30lab

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
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
        result = JSONObject().put("schema", "m30-security-lab/mobile-connection/v1")
            .put("execution_location", "phone").put("backend", id)
            .put("controller_owner", "Android Bluetooth service").put("mechanism", "RFCOMM connection test")
            .put("target", target.address).put("service_uuid", service.toString())
            .put("limits", JSONObject().put("requests", requests).put("duration_s", durationSeconds)
                .put("per_operation_timeout_s", 5).put("workers", 1))
            .put("status", "running").put("baseline", JSONObject.NULL).put("operations", operations)
            .put("target_existing_link_effect", "not_measured")
        worker = Thread({
            try {
                val baseline = attempt(target, service, started, plan)
                synchronized(result) { result.put("baseline", baseline) }
                if (!baseline.optBoolean("connected")) {
                    synchronized(result) { result.put("status", if (stopped.get()) "stopped" else "baseline_failed") }
                } else {
                    var failed = false
                    for (index in 1..requests) {
                        if (!plan.canRunNext(operations.length(), SystemClock.elapsedRealtime() - started, stopped.get())) break
                        val operation = attempt(target, service, started, plan).put("index", index)
                        synchronized(result) { operations.put(operation) }
                        if (!operation.optBoolean("connected")) { failed = true; break }
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
        if (remaining <= 0 || stopped.get()) return JSONObject().put("connected", false).put("error", "deadline/cancelled")
        var socket: BluetoothSocket? = null
        val timedOut = AtomicBoolean(false)
        val timeout = Runnable { timedOut.set(true); try { socket?.close() } catch (_: Exception) {} }
        return try {
            socket = target.createRfcommSocketToServiceRecord(service)
            activeSocket = socket
            handler.postDelayed(timeout, remaining)
            socket.connect()
            JSONObject().put("connected", true).put("elapsed_ms", SystemClock.elapsedRealtime() - begun)
        } catch (e: Exception) {
            JSONObject().put("connected", false).put("timed_out", timedOut.get())
                .put("error", e.javaClass.simpleName).put("elapsed_ms", SystemClock.elapsedRealtime() - begun)
        } finally {
            handler.removeCallbacks(timeout)
            try { socket?.close() } catch (_: Exception) {}
            activeSocket = null
        }
    }

    override fun stop() {
        stopped.set(true)
        try { activeSocket?.close() } catch (_: Exception) {}
        worker?.interrupt()
        synchronized(result) { if (result.optString("status") == "running") result.put("status", "stopped") }
    }

    override fun resultJson(): String = synchronized(result) { result.toString(2) }
    override fun hasResult(): Boolean = synchronized(result) { result.optString("status") != "not_tested" }
}
