package org.cattail.m30lab

import org.junit.Assert.*
import org.junit.Test

class PhoneRunPlanTest {
    @Test fun countDeadlineAndCancelAreHardGates() {
        val plan = PhoneRunPlan(3, 10)
        assertTrue(plan.canRunNext(0, 0, false))
        assertTrue(plan.canRunNext(2, 9999, false))
        assertFalse(plan.canRunNext(3, 0, false))
        assertFalse(plan.canRunNext(0, 10000, false))
        assertFalse(plan.canRunNext(0, 100, true))
        assertEquals(5000L, plan.operationTimeoutMs(0))
        assertEquals(100L, plan.operationTimeoutMs(9900))
        assertEquals(0L, plan.operationTimeoutMs(10000))
    }

    @Test fun invalidUnboundedPlansAreRejected() {
        for ((count, seconds) in listOf(0 to 10, 21 to 10, 1 to 0, 1 to 31)) {
            try { PhoneRunPlan(count, seconds); fail("accepted $count/$seconds") }
            catch (_: IllegalArgumentException) { /* expected */ }
        }
    }
}
