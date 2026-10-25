package org.cattail.m30lab

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object AdvParser {
    fun hex(bytes: ByteArray?): String = bytes?.joinToString("") { "%02X".format(it.toInt() and 0xff) } ?: ""

    fun parse(raw: ByteArray?): JSONObject {
        val fields = JSONArray()
        val out = JSONObject().put("rawHex", hex(raw)).put("fields", fields)
        if (raw == null) return out.put("error", "No raw bytes")
        var offset = 0
        while (offset < raw.size) {
            val length = raw[offset].toInt() and 0xff
            if (length == 0) break
            if (length < 1 || offset + 1 + length > raw.size) {
                out.put("error", "Truncated AD structure at byte $offset")
                break
            }
            val type = raw[offset + 1].toInt() and 0xff
            val data = raw.copyOfRange(offset + 2, offset + 1 + length)
            val item = JSONObject().put("type", type).put("dataHex", hex(data))
            when (type) {
                0x08, 0x09 -> item.put("name", String(data, Charsets.UTF_8))
                0x0a -> if (data.isNotEmpty()) item.put("txPowerDbm", data[0].toInt())
                0xff -> if (data.size >= 2) item.put("companyId", (data[0].toInt() and 0xff) or ((data[1].toInt() and 0xff) shl 8))
                0x02, 0x03 -> item.put("uuid16", uuid16(data))
                0x06, 0x07 -> item.put("uuid128Le", uuid128(data))
            }
            fields.put(item)
            offset += length + 1
        }
        return out
    }

    private fun uuid16(data: ByteArray): JSONArray {
        val values = JSONArray()
        for (i in 0 until data.size - 1 step 2) values.put(String.format(Locale.US, "%04X", (data[i].toInt() and 0xff) or ((data[i + 1].toInt() and 0xff) shl 8)))
        return values
    }

    private fun uuid128(data: ByteArray): JSONArray {
        val values = JSONArray()
        for (i in 0 until data.size - 15 step 16) {
            val b = data.copyOfRange(i, i + 16).reversedArray()
            val h = hex(b)
            values.put("${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}")
        }
        return values
    }
}
