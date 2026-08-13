package com.ai.assistance.operit.data.repository

import android.content.Context
import java.util.ArrayDeque
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UIHierarchyBindingSessionTest {
    @Test
    fun applicationContext_isTheOnlyBindingOwner() {
        val source = mock<Context>()
        val applicationOwner = mock<Context>()
        whenever(source.applicationContext).thenReturn(applicationOwner)

        assertSame(applicationOwner, uiHierarchyBindingOwner(source))
    }

    @Test
    fun providerNotInstalled_reportsNotInstalledWithoutBinding() = runTest {
        val runtime = FakeBindingRuntime(installed = false)
        val session = createSession(runtime)

        assertFalse(session.bind())
        assertSnapshot(
            session = session,
            state = UIHierarchyBindingState.NOT_INSTALLED,
            isBound = false,
            provider = null,
        )
        assertEquals(0, runtime.bindCount)
        assertEquals(0, runtime.unbindCount)
    }

    @Test
    fun unresolvedService_reportsServiceUnresolvedWithoutBinding() = runTest {
        val runtime = FakeBindingRuntime(resolvedService = null)
        val session = createSession(runtime)

        assertFalse(session.bind())
        assertSnapshot(
            session = session,
            state = UIHierarchyBindingState.SERVICE_UNRESOLVED,
            isBound = false,
            provider = null,
        )
        assertEquals(0, runtime.bindCount)
    }

    @Test
    fun disabledAndUnexportedServices_areClassifiedBeforeBinding() = runTest {
        val disabledRuntime =
            FakeBindingRuntime(
                resolvedService = validService.copy(enabled = false),
            )
        val disabledSession = createSession(disabledRuntime)

        assertFalse(disabledSession.bind())
        assertEquals(
            UIHierarchyBindingState.DISABLED,
            disabledSession.snapshot().state,
        )
        assertEquals(0, disabledRuntime.bindCount)

        val unexportedRuntime =
            FakeBindingRuntime(
                resolvedService = validService.copy(exported = false),
            )
        val unexportedSession = createSession(unexportedRuntime)

        assertFalse(unexportedSession.bind())
        assertEquals(
            UIHierarchyBindingState.NOT_EXPORTED_OR_PERMISSION_MISMATCH,
            unexportedSession.snapshot().state,
        )
        assertEquals(0, unexportedRuntime.bindCount)
    }

    @Test
    fun bindReturnedFalse_isNotReportedAsConnectedAndDoesNotUnbind() = runTest {
        val runtime =
            FakeBindingRuntime(
                bindBehaviors =
                    ArrayDeque(
                        listOf(
                            { _: UIHierarchyServiceConnection<String> -> false },
                        )
                    ),
            )
        val session = createSession(runtime)

        assertFalse(session.bind())
        assertSnapshot(
            session = session,
            state = UIHierarchyBindingState.BIND_RETURNED_FALSE,
            isBound = false,
            provider = null,
        )
        assertEquals(1, runtime.bindCount)
        assertEquals(0, runtime.unbindCount)
    }

    @Test
    fun securityException_reportsPermissionMismatchWithoutUnbind() = runTest {
        val runtime =
            FakeBindingRuntime(
                bindBehaviors =
                    ArrayDeque(
                        listOf(
                            { _: UIHierarchyServiceConnection<String> ->
                                throw SecurityException("denied")
                            },
                        )
                    ),
            )
        val session = createSession(runtime)

        assertFalse(session.bind())
        assertSnapshot(
            session = session,
            state = UIHierarchyBindingState.NOT_EXPORTED_OR_PERMISSION_MISMATCH,
            isBound = false,
            provider = null,
        )
        assertEquals(0, runtime.unbindCount)
    }

    @Test
    fun connectionCallbackFailure_releasesRegistrationAndKeepsFailureState() = runTest {
        val runtime =
            FakeBindingRuntime(
                bindBehaviors =
                    ArrayDeque(
                        listOf(
                            { connection: UIHierarchyServiceConnection<String> ->
                                connection.onConnected(null)
                                true
                            },
                        )
                    ),
            )
        val session = createSession(runtime)

        assertFalse(session.bind())
        assertSnapshot(
            session = session,
            state = UIHierarchyBindingState.CONNECTION_FAILED,
            isBound = false,
            provider = null,
        )
        assertEquals(1, runtime.unbindCount)
        assertSame(runtime.lastBoundConnection, runtime.lastUnboundConnection)
    }

    @Test
    fun timeout_releasesRegistrationAndKeepsTimeoutState() = runTest {
        val runtime =
            FakeBindingRuntime(
                bindBehaviors =
                    ArrayDeque(
                        listOf(
                            { _: UIHierarchyServiceConnection<String> -> true },
                        )
                    ),
            )
        val session = createSession(runtime, timeoutMs = 25L)

        assertFalse(session.bind())
        assertSnapshot(
            session = session,
            state = UIHierarchyBindingState.TIMEOUT,
            isBound = false,
            provider = null,
        )
        assertEquals(1, runtime.unbindCount)
        assertSame(runtime.lastBoundConnection, runtime.lastUnboundConnection)
    }

    @Test
    fun connectedRegistration_isUnboundExactlyOnce() = runTest {
        val runtime =
            FakeBindingRuntime(
                bindBehaviors =
                    ArrayDeque(
                        listOf(
                            { connection: UIHierarchyServiceConnection<String> ->
                                connection.onConnected("provider")
                                true
                            },
                        )
                    ),
            )
        val session = createSession(runtime)

        assertTrue(session.bind())
        assertSnapshot(
            session = session,
            state = UIHierarchyBindingState.CONNECTED,
            isBound = true,
            provider = "provider",
        )

        session.unbind()
        session.unbind()

        assertSnapshot(
            session = session,
            state = UIHierarchyBindingState.UNBOUND,
            isBound = false,
            provider = null,
        )
        assertEquals(1, runtime.unbindCount)
        assertSame(runtime.lastBoundConnection, runtime.lastUnboundConnection)
    }

    @Test
    fun repeatedBindFailures_neverReuseAFalseSuccessState() = runTest {
        val runtime =
            FakeBindingRuntime(
                bindBehaviors =
                    ArrayDeque(
                        listOf(
                            { _: UIHierarchyServiceConnection<String> -> false },
                            { _: UIHierarchyServiceConnection<String> -> false },
                        )
                    ),
            )
        val session = createSession(runtime)

        assertFalse(session.bind())
        assertFalse(session.bind())

        assertSnapshot(
            session = session,
            state = UIHierarchyBindingState.BIND_RETURNED_FALSE,
            isBound = false,
            provider = null,
        )
        assertEquals(2, runtime.bindCount)
        assertEquals(0, runtime.unbindCount)
    }

    private fun createSession(
        runtime: FakeBindingRuntime,
        timeoutMs: Long = 1_000L,
    ): UIHierarchyBindingSession<String> =
        UIHierarchyBindingSession(
            runtime = runtime,
            timeoutMs = timeoutMs,
            logSink = UIHierarchyBindingLogSink { _, _, _ -> },
            onSnapshotChanged = {},
        )

    private fun assertSnapshot(
        session: UIHierarchyBindingSession<String>,
        state: UIHierarchyBindingState,
        isBound: Boolean,
        provider: String?,
    ) {
        val snapshot = session.snapshot()
        assertEquals(state, snapshot.state)
        assertEquals(isBound, snapshot.isBound)
        if (provider == null) {
            assertNull(snapshot.provider)
        } else {
            assertEquals(provider, snapshot.provider)
        }
    }

    private class FakeBindingRuntime(
        private val installed: Boolean = true,
        private val resolvedService: UIHierarchyResolvedService? = validService,
        private val bindBehaviors:
            ArrayDeque<(UIHierarchyServiceConnection<String>) -> Boolean> =
            ArrayDeque(),
    ) : UIHierarchyBindingRuntime<String> {
        override val ownerDescription: String = "test.application.Context"

        var bindCount: Int = 0
            private set

        var unbindCount: Int = 0
            private set

        var lastBoundConnection: UIHierarchyServiceConnection<String>? = null
            private set

        var lastUnboundConnection: UIHierarchyServiceConnection<String>? = null
            private set

        override fun isProviderInstalled(): Boolean = installed

        override fun resolveService(): UIHierarchyResolvedService? = resolvedService

        override fun bind(connection: UIHierarchyServiceConnection<String>): Boolean {
            bindCount += 1
            lastBoundConnection = connection
            check(bindBehaviors.isNotEmpty()) {
                "A deterministic bind behavior is required for every bind call"
            }
            return bindBehaviors.removeFirst().invoke(connection)
        }

        override fun unbind(connection: UIHierarchyServiceConnection<String>) {
            unbindCount += 1
            lastUnboundConnection = connection
        }
    }

    companion object {
        private val validService =
            UIHierarchyResolvedService(
                packageName = "com.example.provider",
                serviceName = "AccessibilityProviderService",
                enabled = true,
                exported = true,
                permissionDeclared = true,
            )
    }
}
