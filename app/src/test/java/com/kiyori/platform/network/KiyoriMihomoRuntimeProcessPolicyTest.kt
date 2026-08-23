package com.kiyori.platform.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriMihomoRuntimeProcessPolicyTest {
    @Test
    fun `expected stop with zero exit is stopped and informational`() {
        val outcome = classifyMihomoProcessExit(expectedStop = true, exitCode = 0)

        assertEquals(KiyoriMihomoRuntimePhase.STOPPED, outcome.phase)
        assertEquals(KiyoriNetworkProxyLogLevel.INFO, outcome.logLevel)
        assertEquals(null, outcome.failureKind)
        assertTrue(outcome.message.contains("按请求停止"))
        assertTrue(outcome.message.contains("退出码 0"))
    }

    @Test
    fun `unexpected zero exit remains an error`() {
        val outcome = classifyMihomoProcessExit(expectedStop = false, exitCode = 0)

        assertEquals(KiyoriMihomoRuntimePhase.ERROR, outcome.phase)
        assertEquals(KiyoriNetworkProxyLogLevel.ERROR, outcome.logLevel)
        assertEquals(
            KiyoriMihomoRuntimeFailureKind.UNEXPECTED_PROCESS_EXIT,
            outcome.failureKind,
        )
        assertTrue(outcome.message.contains("意外退出"))
        assertTrue(outcome.message.contains("退出码 0"))
    }

    @Test
    fun `unexpected nonzero exit remains an error with its exit code`() {
        val outcome = classifyMihomoProcessExit(expectedStop = false, exitCode = 137)

        assertEquals(KiyoriMihomoRuntimePhase.ERROR, outcome.phase)
        assertEquals(KiyoriNetworkProxyLogLevel.ERROR, outcome.logLevel)
        assertTrue(outcome.message.contains("退出码 137"))
    }

    @Test
    fun `old monitor cannot update a replacement runtime`() {
        val oldProcess = TestProcess()
        val newProcess = TestProcess()

        assertTrue(isCurrentMihomoProcess(newProcess, newProcess))
        assertFalse(isCurrentMihomoProcess(newProcess, oldProcess))
    }

    @Test
    fun `runtime diagnostic projection keeps generation health ports and stop reason`() {
        val state =
            KiyoriMihomoRuntimeState(
                phase = KiyoriMihomoRuntimePhase.STOPPED,
                runtimeGeneration = 4L,
                mixedPort = 33497,
                controllerPort = 38241,
                controllerHealthy = false,
                mixedPortListening = false,
                stopReason = "vpn_conflict",
                message = "The embedded Mihomo runtime stopped.",
            )

        val diagnostic = formatKiyoriMihomoRuntimeDiagnostic(state)

        assertTrue(diagnostic.contains("phase=STOPPED"))
        assertTrue(diagnostic.contains("runtimeGeneration=4"))
        assertTrue(diagnostic.contains("mixedPort=33497"))
        assertTrue(diagnostic.contains("controllerPort=38241"))
        assertTrue(diagnostic.contains("controllerHealthy=false"))
        assertTrue(diagnostic.contains("mixedPortListening=false"))
        assertTrue(diagnostic.contains("stopReason=vpn_conflict"))
    }

    @Test
    fun `only an unhandled runtime failure is eligible for one recovery trigger`() {
        val unexpectedExit =
            KiyoriMihomoRuntimeState(
                phase = KiyoriMihomoRuntimePhase.ERROR,
                runtimeGeneration = 4L,
                failureKind = KiyoriMihomoRuntimeFailureKind.UNEXPECTED_PROCESS_EXIT,
            )

        assertTrue(shouldAutoRecoverMihomoFailure(unexpectedExit, handledGeneration = null))
        assertFalse(shouldAutoRecoverMihomoFailure(unexpectedExit, handledGeneration = 4L))
        assertFalse(
            shouldAutoRecoverMihomoFailure(
                unexpectedExit.copy(failureKind = KiyoriMihomoRuntimeFailureKind.START_FAILED),
                handledGeneration = null,
            ),
        )
        assertFalse(
            shouldAutoRecoverMihomoFailure(
                unexpectedExit.copy(phase = KiyoriMihomoRuntimePhase.RUNNING),
                handledGeneration = null,
            ),
        )
    }

    @Test
    fun `an unhandled health check failure is eligible for recovery`() {
        val failedHealth =
            KiyoriMihomoRuntimeState(
                phase = KiyoriMihomoRuntimePhase.ERROR,
                runtimeGeneration = 5L,
                failureKind = KiyoriMihomoRuntimeFailureKind.HEALTH_CHECK_FAILED,
            )

        assertTrue(shouldAutoRecoverMihomoFailure(failedHealth, handledGeneration = null))
        assertFalse(shouldAutoRecoverMihomoFailure(failedHealth, handledGeneration = 5L))
    }

    private class TestProcess : Process() {
        override fun getOutputStream() = ByteArrayOutputStream()

        override fun getInputStream() = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = true

        override fun exitValue(): Int = 0

        override fun destroy() = Unit
    }
}
