package org.cattail.m30lab

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import java.io.File
import java.util.UUID

/** Phone-only Bluetooth Classic test. The Linux-HCI path is checked, never opened here. */
class MobileConnectionLab(
    private val activity: Activity,
    private val host: LinearLayout,
    private val adapter: BluetoothAdapter,
    private val ready: (String) -> Boolean,
    private val log: (String) -> Unit,
    private val export: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val found = linkedMapOf<String, BluetoothDevice>()
    private val choices = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val status = label("Ready: select one owned Classic device; no test is running")
    private val detail = label("")
    private val uuidInput = input("Observed RFCOMM service UUID", "")
    private val mechanism = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("RFCOMM service connection (Android)", "L2CAP echo (Linux HCI unavailable)"))
    }
    private val countInput = input("Requests, 1–20", "3")
    private val durationInput = input("Duration seconds, 1–30", "30")
    private var selected: BluetoothDevice? = null
    private var selectedName = ""
    private var scanDeadline: Runnable? = null
    private var discovering = false
    private val backend: PhoneProtocolBackend = AndroidRfcommBackend(handler)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    if (device.type == BluetoothDevice.DEVICE_TYPE_LE || found.containsKey(device.address)) return
                    found[device.address] = device
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    choices.addView(Button(activity).apply {
                        text = "Select ${device.name ?: "(unnamed)"} / ${device.address} / RSSI $rssi / ${typeName(device)}"
                        setOnClickListener { select(device, device.name ?: "(unnamed)") }
                    })
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> stopDiscovery("finished")
                BluetoothDevice.ACTION_UUID -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && device.address == selected?.address) {
                        log("SDP completed for selected Classic device; UUIDs=${device.uuids?.size ?: 0}")
                        showServices(device)
                    }
                }
            }
        }
    }

    init {
        host.addView(label("Execution location: this phone. Android RFCOMM uses the vendor-owned controller. Linux L2CAP echo needs a Linux HCI device and is unavailable on this build."))
        host.addView(status)
        host.addView(detail)
        host.addView(Button(activity).apply { text = "Refresh phone controller access"; setOnClickListener { refresh() } })
        host.addView(Button(activity).apply { text = "Find Bluetooth Classic devices (30 s max)"; setOnClickListener { startDiscovery() } })
        host.addView(Button(activity).apply { text = "Stop Classic discovery"; setOnClickListener { stopDiscovery("manual stop") } })
        host.addView(choices)
        host.addView(Button(activity).apply { text = "Check selected device's Classic services"; setOnClickListener { checkServices() } })
        host.addView(mechanism)
        host.addView(uuidInput)
        host.addView(countInput)
        host.addView(durationInput)
        host.addView(Button(activity).apply { text = "Start bounded RFCOMM service test"; setOnClickListener { start() } })
        host.addView(Button(activity).apply { text = "STOP PHONE CONNECTION TEST"; setOnClickListener { stop("manual stop") } })
        host.addView(Button(activity).apply { text = "Save connection-test JSON"; setOnClickListener { export() } })
        refresh()
        activity.registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_UUID)
        })
    }

    private fun label(value: String) = TextView(activity).apply { text = value; textSize = 14f; setPadding(0, 6, 0, 6) }
    private fun input(hint: String, value: String) = EditText(activity).apply { this.hint = hint; setSingleLine(true); setText(value) }
    private fun typeName(device: BluetoothDevice) = when (device.type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE only"
        else -> "transport unknown"
    }

    fun refresh() {
        val hci = File("/sys/class/bluetooth").listFiles()?.filter { it.name.matches(Regex("hci[0-9]+")) } ?: emptyList()
        val transport = File("/dev/scsc_h4_0").exists()
        detail.text = "Controller owner: ${if (adapter.isEnabled) "Android Bluetooth service (inferred; no fd inspection)" else "unknown / radio off"}\n" +
            "Android RFCOMM: ${if (adapter.isEnabled) "framework path available; target service untested" else "Bluetooth disabled"}\n" +
            "Linux HCI: ${if (hci.isEmpty()) "unsupported: no registered hci device" else "candidate ${hci.joinToString { it.name }}; ownership and socket access unverified"}\n" +
            "Samsung H4 node exists: $transport. A character node is not AF_BLUETOOTH.\n" +
            "L2CAP echo: unsupported on this installed kernel."
    }

    fun select(device: BluetoothDevice, name: String) {
        if (backend.isRunning()) { status.text = "Running: stop the current test before changing target"; return }
        selected = device; selectedName = name
        log("Classic target selected: $name / ${typeName(device)}")
        status.text = "Selected $name / ${device.address} / ${typeName(device)}; service check required"
        showServices(device)
    }

    private fun startDiscovery() {
        if (discovering || !ready("Classic discovery")) return
        found.clear(); choices.removeAllViews()
        try {
            discovering = adapter.startDiscovery()
            if (!discovering) { status.text = "Failed: Android refused Classic discovery"; return }
            scanDeadline = Runnable { stopDiscovery("30-second deadline") }.also { handler.postDelayed(it, 30_000) }
            log("Classic discovery started; 30-second deadline")
            status.text = "Running: Classic discovery, 30-second limit"
        } catch (e: Exception) { status.text = "Failed: Classic discovery: ${e.javaClass.simpleName}" }
    }

    private fun stopDiscovery(reason: String) {
        scanDeadline?.let(handler::removeCallbacks); scanDeadline = null
        if (!discovering) return
        discovering = false
        try { if (adapter.isDiscovering) adapter.cancelDiscovery() } catch (_: Exception) {}
        status.text = "Completed: Classic discovery $reason; ${found.size} selectable devices"
        log("Classic discovery finished: reason=$reason devices=${found.size}")
    }

    private fun checkServices() {
        val device = selected ?: run { status.text = "Select one owned Classic device first"; return }
        if (!ready("service discovery")) return
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) { status.text = "Unsupported: BLE-only device has no RFCOMM service"; return }
        try {
            stopDiscovery("service check")
            log("SDP request started for selected Classic device")
            if (!device.fetchUuidsWithSdp()) status.text = "Failed: Android rejected Classic SDP request"
            else status.text = "Checking Classic services for $selectedName; wait for UUID result"
        } catch (e: Exception) { status.text = "Failed: SDP: ${e.javaClass.simpleName}" }
    }

    private fun showServices(device: BluetoothDevice) {
        val uuids = try { device.uuids?.map { it.uuid.toString() } ?: emptyList() } catch (_: Exception) { emptyList() }
        detail.text = detail.text.toString().substringBefore("\nObserved service UUIDs:") + "\nObserved service UUIDs: ${if (uuids.isEmpty()) "none/not returned" else uuids.joinToString()}"
        if (uuids.contains(AndroidRfcommBackend.FIXTURE_UUID.toString())) {
            uuidInput.setText(AndroidRfcommBackend.FIXTURE_UUID.toString())
            status.text = "Ready: LAB RFCOMM fixture service selected; request/response will be verified"
            return
        }
        if (uuids.isNotEmpty()) status.text = "Ready: select a returned service UUID explicitly"
    }

    fun start() {
        if (backend.isRunning()) { status.text = "Running: one worker already active"; return }
        val device = selected ?: run { status.text = "Select one owned Classic device first"; return }
        if (!ready("RFCOMM test")) return
        if (mechanism.selectedItemPosition != 0) {
            status.text = "Unsupported: L2CAP echo needs Linux HCI; this kernel registers no hci device"
            return
        }
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) { status.text = "Unsupported: BLE-only target"; return }
        val uuid = try { UUID.fromString(uuidInput.text.toString().trim()) } catch (_: Exception) { null }
        if (uuid == null) { status.text = "Enter a service UUID returned by Classic SDP"; return }
        val services = try { device.uuids?.map { it.uuid } ?: emptyList() } catch (_: Exception) { emptyList() }
        if (!services.contains(uuid)) { status.text = "Unsupported: selected UUID was not returned by this device's SDP check"; return }
        val count = countInput.text.toString().toIntOrNull()
        val duration = durationInput.text.toString().toIntOrNull()
        if (count == null || count !in 1..20 || duration == null || duration !in 1..30) {
            status.text = "Use 1–20 requests and a 1–30 second duration"; return
        }
        stopDiscovery("connection test")
        status.text = "Running on this phone: baseline + at most $count requests, ${duration}s deadline"
        backend.start(device, uuid, count, duration) { state ->
            status.text = "$state: phone RFCOMM test; existing-link effect not measured"
            log("Phone RFCOMM test $state; target effect not measured")
        }
    }

    fun stop(reason: String) {
        val running = backend.isRunning()
        backend.stop()
        if (running) status.text = "Stopping: $reason; closing active socket"
    }

    fun onStop() { stopDiscovery("activity stopped"); stop("activity stopped") }
    fun destroy() { onStop(); try { activity.unregisterReceiver(receiver) } catch (_: Exception) {} }
    fun resultJson(): String = backend.resultJson()
    fun hasResult(): Boolean = backend.hasResult()
}
