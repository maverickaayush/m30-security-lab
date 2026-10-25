package org.cattail.m30lab

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CodecTest {
    @Test fun parsesValidAndTruncatedAdvertisement() {
        val valid = byteArrayOf(5, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x4c, 0x41, 0)
        val parsed = AdvParser.parse(valid)
        assertEquals(65535, parsed.getJSONArray("fields").getJSONObject(0).getInt("companyId"))
        assertFalse(parsed.has("error"))
        assertTrue(AdvParser.parse(byteArrayOf(5, 9, 65)).getString("error").contains("Truncated"))
    }
    @Test fun exportsQuoteAndRoundTrip() {
        val item = JSONObject().put("time", "2026-10-26T00:00:00Z").put("address", "AA:BB")
            .put("name", "LAB,\"fixture\"").put("rssi", -55).put("rawHex", "05FF")
            .put("parsed", AdvParser.parse(byteArrayOf(2, 9, 65)))
        val json = ScanCodec.json(listOf(item))
        assertEquals("LAB,\"fixture\"", JSONArray(json).getJSONObject(0).getString("name"))
        val csv = ScanCodec.csv(listOf(item))
        assertTrue(csv.contains("\"LAB,\"\"fixture\"\"\""))
        assertEquals(2, csv.trimEnd().lines().size)
    }
}
