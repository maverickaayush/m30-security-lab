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
    private lateinit var gattUi: GattLab
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
        button("Refresh capabilities") { showCapabilities() }
        button("Request required permissions") { requestLabPermissions() }
        dashboard = text("")
        title("Scanner and advertisement inspector")
        uuidFilter = input("Lab service UUID filter (optional)", labUuid.toString())
        addressFilter = input("Owned fixture address filter (optional)", "")
        button("Start scan (30 s max)") { startScan() }
        button("STOP SCAN") { stopScan("manual stop") }
        button("Export scan JSON") { export("application/json", "scan.json", 1) }
        button("Export scan CSV") { export("text/csv", "scan.csv", 2) }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)
        inspection = text("No advertisement inspected yet")
        title("Synthetic LAB advertising")
        text("UUID $labUuid • marker LAB • one legacy set • no automatic transmission")
        durationInput = input("Duration seconds (5–120)", "30")
        intervalInput = input("Payload change interval ms (1000–10000)", "1000")
        messageInput = input("LAB popup text (4 ASCII; opt-in receiver only)", "LAB")
        modeSpinner = spinner(arrayOf("Low power", "Balanced", "Low latency"))
        powerSpinner = spinner(arrayOf("Ultra low", "Low", "Medium", "High"))
        button("Start single LAB advertisement") { startAdv(false) }
        button("Start bounded LAB sequence") { startAdv(true) }
        button("EMERGENCY STOP ALL LAB ACTIVITY") { stopEverything() }
        title("Owned peripheral GATT and link resilience")
        text("Select a scan result, then use explicit read/write/notify controls below. No automatic writes.")
        gattUi = GattLab(this, root, ::log)
        title("Event log")
        logView = text("")
        showCapabilities()
    }

    private fun title(value: String) { root.addView(TextView(this).apply { text = value; textSize = 20f; setPadding(0, 22, 0, 8) }) }
    private fun text(value: String): TextView = TextView(this).also { it.text = value; it.textSize = 14f; root.addView(it) }
    private fun button(value: String, click: () -> Unit) { root.addView(Button(this).apply { text = value; setOnClickListener { click() } }) }
    private fun input(hint: String, value: String) = EditText(this).also { it.hint = hint; it.setSingleLine(true); it.setText(value); root.addView(it) }
    private fun spinner(items: Array<String>) = Spinner(this).also { it.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items); root.addView(it) }
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
            val item = JSONObject().put("time", stamp.format(Date())).put("address", address)
                .put("addressType", "not exposed on Android ${Build.VERSION.SDK_INT}")
                .put("name", name).put("rssi", result.rssi).put("rawHex", AdvParser.hex(raw))
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
                    text = "Select $name [$address], RSSI ${result.rssi}"
                    setOnClickListener { gattUi.select(result.device, name); inspection.text = item.toString(2); log("Selected owned peripheral $address") }
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
        return AdvertiseData.Builder()
        .addServiceUuid(ParcelUuid(labUuid))
        .addManufacturerData(0xffff, byteArrayOf(0x4c, 0x41, 0x42, (counter and 0xff).toByte()) + tag)
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
