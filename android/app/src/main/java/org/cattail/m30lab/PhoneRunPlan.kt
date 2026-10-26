package org.cattail.m30lab

/** Pure scheduling gate shared by the phone backend and local tests. */
data class PhoneRunPlan(val requests: Int, val durationSeconds: Int) {
    init {
        require(requests in 1..20) { "1–20 requests required" }
        require(durationSeconds in 1..30) { "1–30 seconds required" }
    }

    fun canRunNext(completed: Int, elapsedMs: Long, cancelled: Boolean): Boolean =
        !cancelled && completed < requests && elapsedMs < durationSeconds * 1000L

    fun operationTimeoutMs(elapsedMs: Long): Long =
        (durationSeconds * 1000L - elapsedMs).coerceIn(0L, 12000L)
}
