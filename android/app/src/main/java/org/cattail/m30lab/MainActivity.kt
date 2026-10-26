package org.cattail.m30lab

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : Activity() {
    private val labUuid = UUID.fromString("7f5f42d6-4659-4c15-9fe7-7de7a7033b01")
    private val handler = Handler(Looper.getMainLooper())
    private val records = mutableListOf<JSONObject>()
    private val seen = mutableMapOf<String, BluetoothDevice>()
    private lateinit var adapter: BluetoothAdapter
    private lateinit var root: LinearLayout
    private lateinit var dashboard: TextView
    private lateinit var logView: TextView
    private lateinit var results: LinearLayout
    private lateinit var inspection: TextView
    private lateinit var uuidFilter: EditText
    private lateinit var addressFilter: EditText
    private lateinit var durationInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var modeSpinner: Spinner
    private lateinit var powerSpinner: Spinner
    private lateinit var profileSpinner: Spinner
    private lateinit var stressStatus: TextView
    private lateinit var stressTarget: EditText
    private lateinit var stressController: EditText
    private lateinit var gattUi: GattLab
    private var uiHost: LinearLayout? = null
    private var selectedAddress: String? = null
    private var scanActive = false
    private var scanDeadline: Runnable? = null
    private var advActive = false
    private var advGeneration = 0
    private var advSequence = false
    private var advCounter = 0
    private var advDeadlineMillis = 0L
    private var advIntervalMillis = 1000L
    private var advCallback: AdvertiseCallback? = null
    private val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        title("M30 Bluetooth Research Lab")
        text("Use the sections below. The app only transmits after you press a bounded test button.")
        section("Capability dashboard") {
            button("Refresh capabilities") { showCapabilities() }
            button("Request required permissions") { requestLabPermissions() }
            dashboard = text("")
        }
        section("Find nearby devices") {
            text("Select one owned lab device. BLE scans do not enumerate every Bluetooth Classic device.")
            uuidFilter = input("Lab service UUID filter (optional)", labUuid.toString())
            addressFilter = input("Owned fixture address filter (optional)", "")
            button("Start scan (30 s max)") { startScan() }
            button("STOP SCAN") { stopScan("manual stop") }
            button("Export scan JSON") { export("application/json", "scan.json", 1) }
            button("Export scan CSV") { export("text/csv", "scan.csv", 2) }
            results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            add(results)
            inspection = text("No advertisement inspected yet")
        }
        section("Broadcast a test signal") {
            text("Broadcasts can be received by nearby scanners. Choosing a device does not make a broadcast private.")
            text("UUID $labUuid • synthetic LAB marker • one legacy advertising set")
            durationInput = input("Duration seconds (5–120)", "30")
            intervalInput = input("Multi-profile change interval ms (1000–10000)", "1000")
            messageInput = input("Short test marker (4 ASCII; cooperating receiver only)", "LAB")
            profileSpinner = spinner(arrayOf("LAB-A / baseline", "LAB-B / alternate", "LAB-C / recovery"))
            modeSpinner = spinner(arrayOf("Low power", "Balanced", "Low latency"))
            powerSpinner = spinner(arrayOf("Ultra low", "Low", "Medium", "High"))
            button("Start one LAB signal") { startAdv(false) }
            button("Start multi-profile LAB mode") { startAdv(true) }
            button("EMERGENCY STOP ALL LAB ACTIVITY") { stopEverything() }
        }
        section("Connect to a device") {
            text("The selected fixture appears here. GATT writes always show a payload preview and confirmation.")
            gattUi = GattLab(this, uiHost ?: root, ::log)
        }
        section("Bluetooth stress test") {
            text("This phone has no Linux hci0. The laptop backend is a separate, explicitly invoked lab process; no phone-side disconnect is implied.")
            stressTarget = input("Selected target address (read-only display)", "")
            stressTarget.isEnabled = false
            stressController = input("Laptop controller (for the CLI, usually hci0)", "hci0")
            button("Start phone stress test") { startStress() }
            button("Stop phone stress test") { stopStress() }
            stressStatus = text("Unsupported: no internal Linux HCI controller")
        }
        section("Results and advanced details") {
            text("Callbacks, status codes, raw bytes and cleanup events are retained here for research records.")
            logView = text("")
        }
        showCapabilities()
    }

    private fun add(view: View) { (uiHost ?: root).addView(view) }
    private fun section(label: String, build: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 0, 0, 0) }
        val toggle = Button(this).apply { text = "▼ $label"; setOnClickListener {
            body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            text = if (body.visibility == View.VISIBLE) "▼ $label" else "▶ $label"
        } }
        box.addView(toggle); box.addView(body); root.addView(box)
        val previous = uiHost; uiHost = body
        build()
        uiHost = previous
    }
    private fun title(value: String) { add(TextView(this).apply { text = value; textSize = 20f; setPadding(0, 22, 0, 8) }) }
    private fun text(value: String): TextView = TextView(this).also { it.text = value; it.textSize = 14f; add(it) }
    private fun button(value: String, click: () -> Unit) { add(Button(this).apply { text = value; setOnClickListener { click() } }) }
    private fun input(hint: String, value: String) = EditText(this).also { it.hint = hint; it.setSingleLine(true); it.setText(value); add(it) }
    private fun spinner(items: Array<String>) = Spinner(this).also { it.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items); add(it) }
    private fun log(value: String) {
        val line = "${stamp.format(Date())} $value"
        android.util.Log.i("M30Lab", line)
        logView.text = "$line\n${logView.text}".take(3500)
    }

    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT
    ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun missingPermissions() = requiredPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    private fun requestLabPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) log("All required runtime permissions granted") else requestPermissions(missing.toTypedArray(), 7)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        log("Permission result: " + permissions.indices.joinToString { "${permissions[it]}=${grantResults.getOrNull(it) == PackageManager.PERMISSION_GRANTED}" })
        showCapabilities()
    }
    private fun ready(action: String): Boolean {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) { log("$action blocked: permission missing: $missing"); requestLabPermissions(); return false }
        if (!adapter.isEnabled) { log("$action blocked: Bluetooth is off; enable it in Settings"); return false }
        return true
    }
    private fun showCapabilities() {
        val report = StringBuilder("Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}\n")
        report.append("Permissions: ${requiredPermissions().joinToString { "$it=${checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED}" }}\n")
        report.append("Radio enabled: ${adapter.isEnabled}; BLE feature: ${packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)}\n")
        if (adapter.isEnabled && missingPermissions().isEmpty()) try {
            report.append("Scanner: ${adapter.bluetoothLeScanner != null}; advertiser: ${adapter.bluetoothLeAdvertiser != null}\n")
            report.append("Multiple advertising: ${adapter.isMultipleAdvertisementSupported}\n")
            if (Build.VERSION.SDK_INT >= 26) report.append("Extended advertising: ${adapter.isLeExtendedAdvertisingSupported}; max data: ${adapter.leMaximumAdvertisingDataLength} bytes\n")
            else report.append("Extended advertising: API unavailable\n")
        } catch (e: Exception) { report.append("Capability query error: ${e.javaClass.simpleName}: ${e.message}\n") }
        dashboard.text = report.toString()
    }

    private val scannerCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address ?: "unknown"
            val addressRule = addressFilter.text.toString().trim()
            val uuidRule = uuidFilter.text.toString().trim()
            if (addressRule.isNotEmpty() && !address.equals(addressRule, true)) return
            if (uuidRule.isNotEmpty() && result.scanRecord?.serviceUuids?.none { it.uuid.toString().equals(uuidRule, true) } != false) return
            val raw = result.scanRecord?.bytes
            val parsed = AdvParser.parse(raw)
            val name = result.scanRecord?.deviceName ?: result.device.name ?: ""
            val transport = when (result.device.type) {
                BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "dual/unknown"
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                else -> "unknown"
            }
            val item = JSONObject().put("time", stamp.format(Date())).put("address", address)
                .put("addressType", "not exposed on Android ${Build.VERSION.SDK_INT}")
                .put("name", name).put("rssi", result.rssi).put("transport", transport).put("rawHex", AdvParser.hex(raw))
                .put("serviceUuids", JSONArray(result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList<String>()))
                .put("manufacturerData", JSONObject().apply {
                    result.scanRecord?.manufacturerSpecificData?.let { data ->
                        for (i in 0 until data.size()) put(data.keyAt(i).toString(), AdvParser.hex(data.valueAt(i)))
                    }
                }).put("parsed", parsed)
            if (records.size >= 500) records.removeAt(0)
            records.add(item)
            inspection.text = item.toString(2)
            if (!seen.containsKey(address)) {
                seen[address] = result.device
                results.addView(Button(this@MainActivity).apply {
                    text = "Select ${name.ifBlank { "(unnamed)" }} [$address] • $transport • RSSI ${result.rssi}"
                    setOnClickListener {
                        selectedAddress = address
                        stressTarget.setText(address)
                        gattUi.select(result.device, name)
                        inspection.text = item.toString(2)
                        log("Selected owned peripheral $address ($transport); Classic service support remains unverified")
                    }
                })
            }
            if (records.size % 10 == 1) log("Scan records ${records.size}; latest ${name.ifBlank { address }} RSSI ${result.rssi}")
        }
        override fun onScanFailed(errorCode: Int) { log("Scan failed callback code $errorCode"); stopScan("failure") }
    }
    private fun startScan() {
        if (scanActive || !ready("scan")) return
        val scanner = adapter.bluetoothLeScanner ?: run { log("Scanner unavailable"); return }
        try {
            records.clear(); seen.clear(); results.removeAllViews()
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), scannerCallback)
            scanActive = true
            scanDeadline = Runnable { stopScan("30-second deadline") }.also { handler.postDelayed(it, 30_000) }
            log("Scan started; 30-second hard stop; records capped at 500")
        } catch (e: Exception) { log("Scan start error ${e.javaClass.simpleName}: ${e.message}") }
    }
    private fun stopScan(reason: String) {
        scanDeadline?.let(handler::removeCallbacks); scanDeadline = null
        if (!scanActive) return
        scanActive = false
        try { adapter.bluetoothLeScanner?.stopScan(scannerCallback) } catch (e: Exception) { log("Scan stop error: ${e.message}") }
        log("Scan stopped: $reason; ${records.size} records")
    }

    private fun advData(counter: Int): AdvertiseData {
        val tag = messageInput.text.toString().toByteArray(Charsets.US_ASCII).filter { it in 0x20..0x7e }.take(4).toByteArray()
        val profile = if (advSequence && profileSpinner.count > 0) {
            (profileSpinner.selectedItemPosition + counter) % profileSpinner.count
        } else 0
        return AdvertiseData.Builder()
        .addServiceUuid(ParcelUuid(labUuid))
        .addManufacturerData(0xffff, byteArrayOf(0x4c, 0x41, 0x42, profile.toByte(), (counter and 0xff).toByte()) + tag)
        .setIncludeDeviceName(false).setIncludeTxPowerLevel(false).build()
    }
    private fun advSettings(): AdvertiseSettings {
        val modes = intArrayOf(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER, AdvertiseSettings.ADVERTISE_MODE_BALANCED, AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        val powers = intArrayOf(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW, AdvertiseSettings.ADVERTISE_TX_POWER_LOW, AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM, AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        return AdvertiseSettings.Builder().setAdvertiseMode(modes[modeSpinner.selectedItemPosition])
            .setTxPowerLevel(powers[powerSpinner.selectedItemPosition]).setConnectable(false).setTimeout(0).build()
    }
    private fun startAdv(sequence: Boolean) {
        if (advActive || !ready("advertise")) return
        if (adapter.bluetoothLeAdvertiser == null) { log("Advertiser unavailable"); return }
        val duration = (durationInput.text.toString().toLongOrNull() ?: 30L).coerceIn(5L, 120L)
        advIntervalMillis = (intervalInput.text.toString().toLongOrNull() ?: 1000L).coerceIn(1000L, 10000L)
        advDeadlineMillis = SystemClock.elapsedRealtime() + duration * 1000
        advSequence = sequence; advCounter = 0; advActive = true; advGeneration++
        val generation = advGeneration
        handler.postDelayed({ if (advGeneration == generation) stopAdv("hard deadline") }, duration * 1000)
        log("LAB ${if (sequence) "sequence" else "single"} requested: ${duration}s, ${advIntervalMillis}ms, one set; callback is not RF proof")
        issueAdvertise(generation)
    }
    private fun issueAdvertise(generation: Int) {
        if (!advActive || generation != advGeneration || SystemClock.elapsedRealtime() >= advDeadlineMillis) { stopAdv("deadline before start"); return }
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                if (!advActive || generation != advGeneration) { adapter.bluetoothLeAdvertiser?.stopAdvertising(this); return }
                log("Advertising callback success, payload LAB ${advCounter}; mode ${settingsInEffect.mode}, power ${settingsInEffect.txPowerLevel}")
                if (advSequence) handler.postDelayed({
                    if (advActive && generation == advGeneration && SystemClock.elapsedRealtime() < advDeadlineMillis) {
                        try { adapter.bluetoothLeAdvertiser?.stopAdvertising(this) } catch (_: Exception) {}
                        advCallback = null; advCounter++; issueAdvertise(generation)
                    }
                }, advIntervalMillis)
            }
            override fun onStartFailure(errorCode: Int) { log("Advertising callback failure code $errorCode"); stopAdv("callback failure") }
        }
        advCallback = callback
        try { adapter.bluetoothLeAdvertiser?.startAdvertising(advSettings(), advData(advCounter), callback) }
        catch (e: Exception) { log("Advertising start error ${e.javaClass.simpleName}: ${e.message}"); stopAdv("exception") }
    }
    private fun stopAdv(reason: String) {
        if (!advActive && advCallback == null) return
        advActive = false; advGeneration++
        advCallback?.let { try { adapter.bluetoothLeAdvertiser?.stopAdvertising(it) } catch (e: Exception) { log("Advertising stop error: ${e.message}") } }
        advCallback = null
        log("Advertising stopped: $reason")
    }
    private fun stopEverything() { stopScan("emergency stop"); stopAdv("emergency stop"); gattUi.disconnect(); log("All lab activity stopped") }

    private fun startStress() {
        stressStatus.text = "Unsupported: this Android image exposes no Linux hci0. Use host/blue_stress.py only with a named owned Classic target."
        log("Phone stress test not started: no internal Linux HCI backend; laptop execution is separate")
    }

    private fun stopStress() {
        stressStatus.text = "Ready: no phone-side stress session exists"
        log("Phone stress stop requested; no phone-side process was running")
    }

    private fun export(type: String, filename: String, request: Int) {
        if (records.isEmpty()) { log("No scan records to export"); return }
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type = type; putExtra(Intent.EXTRA_TITLE, filename) }, request)
    }
    @Deprecated("Uses Android 10 activity result API for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            val content = if (requestCode == 1) ScanCodec.json(records) else ScanCodec.csv(records)
            contentResolver.openOutputStream(data.data!!)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            log("Exported ${records.size} records")
        } catch (e: Exception) { log("Export error ${e.message}") }
    }
    override fun onStop() { stopScan("activity stopped"); stopAdv("activity stopped"); super.onStop() }
    override fun onDestroy() { stopEverything(); super.onDestroy() }
}
