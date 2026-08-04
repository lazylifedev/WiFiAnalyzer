package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.billing.DataOperationGate
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.billing.RestrictedDataOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataOperationGateTest {
    @Test fun freeBlocksEveryRestrictedOperationWithoutCallingAction() {
        var calls = 0
        val gate = DataOperationGate { FeatureAccessPolicy(isPro = false) }
        RestrictedDataOperation.entries.forEach { operation ->
            val result = gate.run(operation) { calls++ }
            assertTrue(result.isFailure)
            assertEquals("${operation.name}_REQUIRES_PRO", result.exceptionOrNull()?.message)
        }
        assertEquals(0, calls)
    }

    @Test fun proRunsEveryOperation() {
        var calls = 0
        val gate = DataOperationGate { FeatureAccessPolicy(isPro = true, limits = com.lazyapps.wifianalyzer.billing.FeatureLimits(null, null, 9)) }
        RestrictedDataOperation.entries.forEach { assertTrue(gate.run(it) { ++calls }.isSuccess) }
        assertEquals(4, calls)
    }

    @Test fun stateIsReadAtTheMomentOfExecution() {
        var pro = true
        var calls = 0
        val gate = DataOperationGate { FeatureAccessPolicy(isPro = pro) }
        pro = false
        assertFalse(gate.run(RestrictedDataOperation.RESTORE) { ++calls }.isSuccess)
        assertEquals(0, calls)
    }
}
