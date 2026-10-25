package org.cattail.m30lab

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.UUID

class GattLab(private val activity: Activity, private val root: LinearLayout, private val log: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private var selected: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private var chosen: BluetoothGattCharacteristic? = null
    private var cyclesRemaining = 0
    private var totalCycles = 0
    private var failures = 0
    private var runStart = 0L
    private var connectStart = 0L
    private var generation = 0
    private var discoveryDone = false
    private val state = TextView(activity)
    private val services = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val writeInput = EditText(activity).apply { hint = "Hex payload for selected characteristic (max 20 bytes)"; setSingleLine(true) }

    init {
        root.addView(state)
        button("Connect selected peripheral") { connect(false) }
        button("Discover services") { if (gatt?.discoverServices() != true) log("Service discovery request rejected") }
        root.addView(services)
        root.addView(writeInput)
        button("Read selected characteristic") { chosen?.let { if (gatt?.readCharacteristic(it) != true) log("Read request rejected") } ?: log("Select a characteristic first") }
        button("Write selected characteristic (preview and confirm)") { writeChosen() }
        button("Subscribe selected notifications") { notification(true) }
        button("Unsubscribe selected notifications") { notification(false) }
        button("Disconnect local GATT link") { disconnect() }
        button("Run 3 bounded connect / disconnect cycles") { startResilience() }
        state.text = "No owned peripheral selected"
    }
    private fun button(label: String, click: () -> Unit) { root.addView(Button(activity).apply { text = label; setOnClickListener { click() } }) }
    fun select(device: BluetoothDevice, name: String) { disconnect(); selected = device; state.text = "Selected: $name / ${device.address}"; services.removeAllViews() }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) { handler.post {
            if (g !== gatt) return@post
            log("GATT connection status=$status state=$newState latency=${SystemClock.elapsedRealtime() - connectStart}ms")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!g.discoverServices()) { log("Discovery request failed"); if (cyclesRemaining > 0) failCycle() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close(); if (gatt === g) gatt = null
                    if (cyclesRemaining > 0) { cyclesRemaining--; if (status != BluetoothGatt.GATT_SUCCESS) failures++; nextCycle() }
                }
            }
        } }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { handler.post {
            if (g !== gatt) return@post
            discoveryDone = true
            log("Service discovery status=$status count=${g.services.size}")
            services.removeAllViews()
            if (status == BluetoothGatt.GATT_SUCCESS) for (service in g.services) {
                services.addView(TextView(activity).apply { text = "Service ${service.uuid}" })
                for (c in service.characteristics) services.addView(Button(activity).apply {
                    text = "Select ${c.uuid} properties=0x${c.properties.toString(16)} permissions=0x${c.permissions.toString(16)}"
                    setOnClickListener { chosen = c; log("Characteristic selected ${c.uuid}") }
                })
            }
            if (cyclesRemaining > 0) {
                if (status != BluetoothGatt.GATT_SUCCESS) failures++
                handler.postDelayed({ if (g === gatt) g.disconnect() }, 1000)
            }
        } }
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { handler.post { log("Read ${c.uuid} status=$status value=${AdvParser.hex(c.value)}") } }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { handler.post { log("Write ${c.uuid} status=$status") } }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) { handler.post { log("Notification ${c.uuid} value=${AdvParser.hex(c.value)}") } }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { handler.post { log("Descriptor write ${d.uuid} status=$status") } }
    }
    private fun connect(fromSequence: Boolean) {
        val device = selected ?: run { log("Select an owned peripheral from scan results first"); return }
        if (gatt != null) { log("A GATT link is already active"); return }
        connectStart = SystemClock.elapsedRealtime()
        discoveryDone = false
        try {
            gatt = device.connectGatt(activity, false, callback, BluetoothDevice.TRANSPORT_LE)
            log("GATT connect requested to selected peripheral")
            val ticket = generation
            handler.postDelayed({ if (ticket == generation && gatt != null && !discoveryDone && SystemClock.elapsedRealtime() - connectStart >= 10_000) {
                log("GATT connection/discovery timeout (10 s)"); failures++; gatt?.disconnect(); gatt?.close(); gatt = null
                if (fromSequence && cyclesRemaining > 0) { cyclesRemaining--; nextCycle() }
            } }, 10_000)
        } catch (e: Exception) { log("GATT connect error ${e.javaClass.simpleName}: ${e.message}"); if (fromSequence) failCycle() }
    }
    private fun writeChosen() {
        val c = chosen ?: run { log("Select a characteristic first"); return }
        if (c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) { log("Selected characteristic is not writable"); return }
        val value = writeInput.text.toString().replace(" ", "")
        if (value.length > 40 || value.length % 2 != 0 || !value.matches(Regex("[0-9A-Fa-f]*"))) { log("Write input must be even-length hex, at most 20 bytes"); return }
        val bytes = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        android.app.AlertDialog.Builder(activity).setTitle("Confirm intentional GATT write")
            .setMessage("Characteristic ${c.uuid}\nPayload ${AdvParser.hex(bytes)} (${bytes.size} bytes)")
            .setNegativeButton("Cancel", null).setPositiveButton("Write") { _, _ ->
                c.writeType = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                c.value = bytes
                if (gatt?.writeCharacteristic(c) != true) log("Write request rejected") else log("Write requested: ${c.uuid} ${AdvParser.hex(bytes)}")
            }.show()
    }
    private fun notification(enabled: Boolean) {
        val c = chosen ?: run { log("Select a characteristic first"); return }
        if (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0) { log("Selected characteristic has no notify/indicate property"); return }
        val g = gatt ?: run { log("Connect first"); return }
        if (!g.setCharacteristicNotification(c, enabled)) { log("Local notification request rejected"); return }
        val d = c.getDescriptor(cccd) ?: run { log("No CCCD on characteristic"); return }
        d.value = if (!enabled) BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE else if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        if (!g.writeDescriptor(d)) log("CCCD write request rejected") else log("CCCD ${if (enabled) "subscribe" else "unsubscribe"} requested")
    }
    private fun startResilience() {
        if (selected == null) { log("Select owned peripheral first"); return }
        disconnect(); generation++; cyclesRemaining = 3; totalCycles = 3; failures = 0; runStart = SystemClock.elapsedRealtime()
        val ticket = generation
        handler.postDelayed({ if (ticket == generation && cyclesRemaining > 0) { log("Resilience hard deadline 45s"); finishResilience() } }, 45_000)
        nextCycle()
    }
    private fun nextCycle() {
        if (cyclesRemaining <= 0) { finishResilience(); return }
        log("Resilience cycle ${totalCycles - cyclesRemaining + 1}/$totalCycles")
        handler.postDelayed({ if (cyclesRemaining > 0 && gatt == null) connect(true) }, 500)
    }
    private fun failCycle() { cyclesRemaining--; failures++; gatt?.close(); gatt = null; nextCycle() }
    private fun finishResilience() {
        val done = totalCycles - cyclesRemaining
        cyclesRemaining = 0; generation++; gatt?.disconnect(); gatt?.close(); gatt = null
        log("Resilience complete: cycles=$done/$totalCycles failures=$failures elapsed=${SystemClock.elapsedRealtime() - runStart}ms")
    }
    fun disconnect() { cyclesRemaining = 0; generation++; gatt?.disconnect(); gatt?.close(); gatt = null; chosen = null }
}
