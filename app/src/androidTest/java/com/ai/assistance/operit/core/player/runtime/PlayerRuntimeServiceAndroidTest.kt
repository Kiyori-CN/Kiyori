package com.ai.assistance.operit.core.player.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerRuntimeServiceAndroidTest {
    @Test
    fun initialize_reportsDistinctProcessAndIncreasingEventSequence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val ready = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val runtimePid = AtomicInteger(0)
        val readySequence = AtomicLong(0L)
        val closeSequence = AtomicLong(0L)
        var runtime: IPlayerRuntime? = null

        val callback =
            object : IPlayerRuntimeCallback.Stub() {
                override fun onRuntimeReady(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    commandId: Long,
                    processId: Int,
                ) {
                    runtimePid.set(processId)
                    readySequence.set(eventSequence)
                    ready.countDown()
                }

                override fun onCommandCompleted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    commandId: Long,
                ) {
                    if (commandId == CLOSE_COMMAND_ID) {
                        closeSequence.set(eventSequence)
                        closed.countDown()
                    }
                }

                override fun onCommandFailed(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    commandId: Long,
                    operation: String?,
                    message: String?,
                ) = Unit

                override fun onSurfaceAttached(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    commandId: Long,
                    surfaceGeneration: Long,
                ) = Unit

                override fun onSurfaceDetached(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    commandId: Long,
                    surfaceGeneration: Long,
                ) = Unit

                override fun onPlaybackSnapshot(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    snapshot: PlayerRuntimePlaybackSnapshot?,
                ) = Unit

                override fun onFileLoaded(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    loadCommandId: Long,
                    tracks: PlayerRuntimeTrackSnapshot?,
                ) = Unit

                override fun onNaturalEnd(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                ) = Unit

                override fun onRuntimeError(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    message: String?,
                ) = Unit

                override fun onDiagnosticLog(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    level: Int,
                    tag: String?,
                    message: String?,
                ) = Unit

                override fun onThumbnailReady(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    commandId: Long,
                    positionSeconds: Double,
                    bitmap: Bitmap?,
                ) = Unit

                override fun onScreenshotCompleted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    commandId: Long,
                    path: String?,
                ) = Unit
            }

        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    runtime = IPlayerRuntime.Stub.asInterface(service)
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }

        val bound =
            context.bindService(
                Intent(context, PlayerRuntimeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        assertTrue(bound)
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val activeRuntime = requireNotNull(runtime)
            activeRuntime.registerCallback(RUNTIME_GENERATION, callback)
            activeRuntime.initialize(
                RUNTIME_GENERATION,
                INITIALIZE_COMMAND_ID,
                PlayerRuntimeConfig(
                    decoderPresetId = "fast",
                    gpuNextEnabled = false,
                    vulkanEnabled = false,
                    preciseSeeking = true,
                    networkCachePolicyId = "balanced",
                    subtitleScale = 1.0,
                    volumeBoostEnabled = false,
                    shaderFiles = emptyList(),
                ),
            )
            assertTrue(ready.await(20, TimeUnit.SECONDS))
            assertNotEquals(Process.myPid(), runtimePid.get())

            activeRuntime.close(RUNTIME_GENERATION, CLOSE_COMMAND_ID)
            assertTrue(closed.await(10, TimeUnit.SECONDS))
            assertTrue(closeSequence.get() > readySequence.get())
        } finally {
            context.unbindService(connection)
        }
    }

    private companion object {
        const val RUNTIME_GENERATION = 1L
        const val INITIALIZE_COMMAND_ID = 1L
        const val CLOSE_COMMAND_ID = 2L
    }
}
