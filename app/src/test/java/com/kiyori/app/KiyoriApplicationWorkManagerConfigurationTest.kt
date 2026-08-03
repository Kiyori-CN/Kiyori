package com.kiyori.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KiyoriApplicationWorkManagerConfigurationTest {
    @Test
    fun jobSchedulerRangeSatisfiesWorkManagerAndExcludesBrowserRuntime() {
        // setJobSchedulerJobIdRange validates immediately, before build() needs Android's Looper.
        androidx.work.Configuration.Builder().applyKiyoriJobSchedulerIdRange()

        assertEquals(0x5000, KIYORI_WORK_MANAGER_JOB_ID_MIN)
        assertEquals(0x53E8, KIYORI_WORK_MANAGER_JOB_ID_MAX)
        assertEquals(
            1_000,
            KIYORI_WORK_MANAGER_JOB_ID_MAX - KIYORI_WORK_MANAGER_JOB_ID_MIN,
        )
        assertFalse(0x4B10 in KIYORI_WORK_MANAGER_JOB_ID_MIN..KIYORI_WORK_MANAGER_JOB_ID_MAX)
    }
}
