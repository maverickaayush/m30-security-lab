package org.cattail.m30lab

import org.json.JSONArray
import org.json.JSONObject

object ScanCodec {
    fun json(records: List<JSONObject>): String = JSONArray(records).toString(2)
    fun csv(records: List<JSONObject>): String {
        val header = "time,address,name,rssi,rawHex,parsedJson\n"
        return header + records.joinToString("") { r ->
            listOf("time", "address", "name", "rssi", "rawHex").joinToString(",") { quote(r.optString(it)) } +
                "," + quote(r.optJSONObject("parsed")?.toString() ?: "") + "\n"
        }
    }
    private fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
