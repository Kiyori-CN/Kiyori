package com.ai.assistance.operit.core.ffmpeg.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FFmpegRuntimeServiceAndroidTest {
    @Test
    fun runtimeInfoExecutesInDistinctProcessWithOrderedCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val runtimePid = AtomicInteger(0)
        val startedSequence = AtomicLong(0L)
        val completedSequence = AtomicLong(0L)
        var runtime: IFFmpegRuntime? = null
        var result: FFmpegRuntimeResult? = null

        val callback =
            object : IFFmpegRuntimeCallback.Stub() {
                override fun onRequestStarted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    requestId: String?,
                    sessionId: Long,
                    processId: Int,
                ) {
                    if (requestId == INFO_REQUEST_ID) {
                        runtimePid.set(processId)
                        startedSequence.set(eventSequence)
                        started.countDown()
                    }
                }

                override fun onRequestCompleted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    resultValue: FFmpegRuntimeResult?,
                ) {
                    if (resultValue?.requestId == INFO_REQUEST_ID) {
                        result = resultValue
                        completedSequence.set(eventSequence)
                        completed.countDown()
                    }
                }

                override fun onRequestFailed(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    failure: FFmpegRuntimeFailure?,
                ) = Unit
            }

        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    runtime = IFFmpegRuntime.Stub.asInterface(service)
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }

        assertTrue(
            context.bindService(
                Intent(context, FFmpegRuntimeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            ),
        )
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val activeRuntime = requireNotNull(runtime)
            assertTrue(activeRuntime.registerCallback(RUNTIME_GENERATION, callback))
            assertTrue(
                activeRuntime.submit(
                    RUNTIME_GENERATION,
                    FFmpegRuntimeRequest(
                        requestId = INFO_REQUEST_ID,
                        operationWireValue = FFmpegRuntimeOperation.RUNTIME_INFO.wireValue,
                        informationSectionWireValue =
                            FFmpegRuntimeInformationSection.SUMMARY.wireValue,
                    ),
                ),
            )

            assertTrue(started.await(10, TimeUnit.SECONDS))
            assertTrue(completed.await(20, TimeUnit.SECONDS))
            val completedResult = requireNotNull(result)
            assertNotEquals(Process.myPid(), runtimePid.get())
            assertEquals(runtimePid.get(), completedResult.processId)
            assertTrue(completedSequence.get() > startedSequence.get())
            assertEquals(FFmpegRuntimeTerminalState.SUCCEEDED, completedResult.terminalState)
            assertTrue(
                requireNotNull(completedResult.runtimeInformation)
                    .wrapperVersion
                    .contains("8.1.7-kiyori-n9.0.1-r6"),
            )
            assertTrue(
                requireNotNull(completedResult.runtimeInformation)
                    .ffmpegVersion
                    .contains("9.0.1"),
            )
            val outputFile = File(completedResult.outputLogPath)
            assertTrue(outputFile.canonicalFile.parentFile == ffmpegRuntimeLogDirectory(context.cacheDir).canonicalFile)
            assertTrue(outputFile.readText().contains("ffmpeg version", ignoreCase = true))
            outputFile.delete()
        } finally {
            context.unbindService(connection)
        }
    }

    @Test
    fun queuedCommandsKeepIndependentSessionsAndTerminalResults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val sessions = ConcurrentHashMap<String, Long>()
        val results = ConcurrentHashMap<String, FFmpegRuntimeResult>()
        var runtime: IFFmpegRuntime? = null

        val callback =
            object : IFFmpegRuntimeCallback.Stub() {
                override fun onRequestStarted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    requestId: String?,
                    sessionId: Long,
                    processId: Int,
                ) {
                    if (requestId != null) {
                        sessions[requestId] = sessionId
                    }
                }

                override fun onRequestCompleted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    result: FFmpegRuntimeResult?,
                ) {
                    if (result != null && result.requestId in CONCURRENT_REQUEST_IDS) {
                        results[result.requestId] = result
                        completed.countDown()
                    }
                }

                override fun onRequestFailed(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    failure: FFmpegRuntimeFailure?,
                ) = Unit
            }

        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    runtime = IFFmpegRuntime.Stub.asInterface(service)
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }

        assertTrue(
            context.bindService(
                Intent(context, FFmpegRuntimeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            ),
        )
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val activeRuntime = requireNotNull(runtime)
            assertTrue(activeRuntime.registerCallback(RUNTIME_GENERATION, callback))
            assertTrue(
                activeRuntime.submit(
                    RUNTIME_GENERATION,
                    commandRequest(CONCURRENT_REQUEST_IDS[0], "-version"),
                ),
            )
            assertTrue(
                activeRuntime.submit(
                    RUNTIME_GENERATION,
                    commandRequest(CONCURRENT_REQUEST_IDS[1], "-filters"),
                ),
            )

            assertTrue(completed.await(20, TimeUnit.SECONDS))
            assertEquals(2, sessions.values.toSet().size)
            CONCURRENT_REQUEST_IDS.forEach { requestId ->
                val result = requireNotNull(results[requestId])
                assertEquals(FFmpegRuntimeTerminalState.SUCCEEDED, result.terminalState)
                File(result.outputLogPath).delete()
            }
        } finally {
            context.unbindService(connection)
        }
    }

    @Test
    fun queuedCancellationCompletesWithoutStartingANativeSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val blockerStarted = CountDownLatch(1)
        val cancelledTerminal = CountDownLatch(1)
        val cancelledStartedCount = AtomicInteger(0)
        var runtime: IFFmpegRuntime? = null
        var cancelledResult: FFmpegRuntimeResult? = null

        val callback =
            object : IFFmpegRuntimeCallback.Stub() {
                override fun onRequestStarted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    requestId: String?,
                    sessionId: Long,
                    processId: Int,
                ) {
                    when (requestId) {
                        QUEUE_BLOCKER_REQUEST_ID -> blockerStarted.countDown()
                        QUEUED_CANCEL_REQUEST_ID -> cancelledStartedCount.incrementAndGet()
                    }
                }

                override fun onRequestCompleted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    result: FFmpegRuntimeResult?,
                ) {
                    if (result?.requestId == QUEUED_CANCEL_REQUEST_ID) {
                        cancelledResult = result
                        cancelledTerminal.countDown()
                    }
                }

                override fun onRequestFailed(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    failure: FFmpegRuntimeFailure?,
                ) = Unit
            }
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    runtime = IFFmpegRuntime.Stub.asInterface(service)
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }

        assertTrue(
            context.bindService(
                Intent(context, FFmpegRuntimeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            ),
        )
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val activeRuntime = requireNotNull(runtime)
            assertTrue(activeRuntime.registerCallback(RUNTIME_GENERATION, callback))
            assertTrue(
                activeRuntime.submit(
                    RUNTIME_GENERATION,
                    argumentRequest(
                        QUEUE_BLOCKER_REQUEST_ID,
                        listOf(
                            "-re",
                            "-f",
                            "lavfi",
                            "-i",
                            "testsrc=size=16x16:rate=1",
                            "-t",
                            "30",
                            "-f",
                            "null",
                            "-",
                        ),
                    ),
                ),
            )
            assertTrue(blockerStarted.await(10, TimeUnit.SECONDS))
            assertTrue(
                activeRuntime.submit(
                    RUNTIME_GENERATION,
                    commandRequest(QUEUED_CANCEL_REQUEST_ID, "-version"),
                ),
            )
            assertTrue(activeRuntime.cancel(RUNTIME_GENERATION, QUEUED_CANCEL_REQUEST_ID))

            assertTrue(cancelledTerminal.await(10, TimeUnit.SECONDS))
            val result = requireNotNull(cancelledResult)
            assertEquals(0, cancelledStartedCount.get())
            assertEquals(0L, result.sessionId)
            assertEquals(FFmpegRuntimeTerminalState.CANCELLED, result.terminalState)
            assertEquals(FFMPEG_RUNTIME_CANCEL_RETURN_CODE, result.returnCode)
            File(result.outputLogPath).delete()
            activeRuntime.cancel(RUNTIME_GENERATION, QUEUE_BLOCKER_REQUEST_ID)
        } finally {
            context.unbindService(connection)
        }
    }

    @Test
    fun transcodeSmokeMatrixCoversLavfiDecodeCopyAudioAnd720pInFifoOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val sourceFile = File(context.cacheDir, "ffmpeg-runtime-smoke-source.mp4")
        val copyFile = File(context.cacheDir, "ffmpeg-runtime-smoke-copy.mp4")
        val audioFile = File(context.cacheDir, "ffmpeg-runtime-smoke-audio.mp3")
        val output720pFile = File(context.cacheDir, "ffmpeg-runtime-smoke-720p.mp4")
        val outputFiles = listOf(sourceFile, copyFile, audioFile, output720pFile)
        outputFiles.forEach(File::delete)

        val requests =
            listOf(
                "55555555555555555555555555555555" to
                    argumentRequest(
                        "55555555555555555555555555555555",
                        listOf(
                            "-f",
                            "lavfi",
                            "-i",
                            "testsrc=duration=1:size=320x240:rate=10",
                            "-f",
                            "null",
                            "-",
                        ),
                    ),
                "66666666666666666666666666666666" to
                    argumentRequest(
                        "66666666666666666666666666666666",
                        listOf(
                            "-y",
                            "-f",
                            "lavfi",
                            "-i",
                            "testsrc2=duration=1:size=320x240:rate=10",
                            "-f",
                            "lavfi",
                            "-i",
                            "sine=frequency=440:duration=1",
                            "-shortest",
                            "-c:v",
                            "libopenh264",
                            "-profile:v",
                            "constrained_baseline",
                            "-pix_fmt",
                            "yuv420p",
                            "-c:a",
                            "aac",
                            sourceFile.absolutePath,
                        ),
                    ),
                "77777777777777777777777777777777" to
                    argumentRequest(
                        "77777777777777777777777777777777",
                        listOf(
                            "-i",
                            sourceFile.absolutePath,
                            "-t",
                            "1",
                            "-f",
                            "null",
                            "-",
                        ),
                    ),
                "88888888888888888888888888888888" to
                    argumentRequest(
                        "88888888888888888888888888888888",
                        listOf(
                            "-y",
                            "-i",
                            sourceFile.absolutePath,
                            "-t",
                            "1",
                            "-c",
                            "copy",
                            copyFile.absolutePath,
                        ),
                    ),
                "99999999999999999999999999999999" to
                    argumentRequest(
                        "99999999999999999999999999999999",
                        listOf(
                            "-y",
                            "-i",
                            sourceFile.absolutePath,
                            "-vn",
                            "-c:a",
                            "libmp3lame",
                            "-q:a",
                            "2",
                            audioFile.absolutePath,
                        ),
                    ),
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" to
                    argumentRequest(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        listOf(
                            "-y",
                            "-i",
                            sourceFile.absolutePath,
                            "-s",
                            "1280x720",
                            "-c:v",
                            "libopenh264",
                            "-profile:v",
                            "constrained_baseline",
                            "-pix_fmt",
                            "yuv420p",
                            "-c:a",
                            "aac",
                            output720pFile.absolutePath,
                        ),
                    ),
            )
        val requestIds = requests.map { (requestId, _) -> requestId }
        val terminal = CountDownLatch(requests.size)
        val startedOrder = CopyOnWriteArrayList<String>()
        val results = ConcurrentHashMap<String, FFmpegRuntimeResult>()
        val failures = ConcurrentHashMap<String, FFmpegRuntimeFailure>()
        var runtime: IFFmpegRuntime? = null

        val callback =
            object : IFFmpegRuntimeCallback.Stub() {
                override fun onRequestStarted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    requestId: String?,
                    sessionId: Long,
                    processId: Int,
                ) {
                    if (requestId in requestIds) {
                        startedOrder += requireNotNull(requestId)
                    }
                }

                override fun onRequestCompleted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    result: FFmpegRuntimeResult?,
                ) {
                    if (result != null && result.requestId in requestIds) {
                        results[result.requestId] = result
                        terminal.countDown()
                    }
                }

                override fun onRequestFailed(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    failure: FFmpegRuntimeFailure?,
                ) {
                    if (failure != null && failure.requestId in requestIds) {
                        failures[failure.requestId] = failure
                        terminal.countDown()
                    }
                }
            }
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    runtime = IFFmpegRuntime.Stub.asInterface(service)
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }

        assertTrue(
            context.bindService(
                Intent(context, FFmpegRuntimeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            ),
        )
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val activeRuntime = requireNotNull(runtime)
            assertTrue(activeRuntime.registerCallback(RUNTIME_GENERATION, callback))
            requests.forEach { (_, request) ->
                assertTrue(activeRuntime.submit(RUNTIME_GENERATION, request))
            }

            assertTrue(terminal.await(120, TimeUnit.SECONDS))
            assertTrue(failures.isEmpty())
            assertEquals(requestIds, startedOrder.toList())
            requestIds.forEach { requestId ->
                val result = requireNotNull(results[requestId])
                assertEquals(FFmpegRuntimeTerminalState.SUCCEEDED, result.terminalState)
                File(result.outputLogPath).delete()
            }
            outputFiles.forEach { output ->
                assertTrue("$output is missing or empty", output.isFile && output.length() > 0L)
            }
        } finally {
            context.unbindService(connection)
            outputFiles.forEach(File::delete)
        }
    }

    @Test
    fun replacingCallbackTerminatesOldGenerationExactlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val oldStarted = CountDownLatch(1)
        val oldTerminal = CountDownLatch(1)
        val oldStartedSequence = AtomicLong(0L)
        val oldTerminalSequence = AtomicLong(0L)
        val oldTerminalCount = AtomicInteger(0)
        val oldFailureCode = AtomicInteger(-1)
        val newOwnerOldRequestTerminalCount = AtomicInteger(0)
        var runtime: IFFmpegRuntime? = null

        val oldCallback =
            object : IFFmpegRuntimeCallback.Stub() {
                override fun onRequestStarted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    requestId: String?,
                    sessionId: Long,
                    processId: Int,
                ) {
                    if (
                        runtimeGeneration == RUNTIME_GENERATION &&
                            requestId == REPLACED_REQUEST_ID
                    ) {
                        oldStartedSequence.set(eventSequence)
                        oldStarted.countDown()
                    }
                }

                override fun onRequestCompleted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    result: FFmpegRuntimeResult?,
                ) {
                    if (result?.requestId == REPLACED_REQUEST_ID) {
                        oldTerminalCount.incrementAndGet()
                        oldTerminalSequence.set(eventSequence)
                        oldTerminal.countDown()
                    }
                }

                override fun onRequestFailed(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    failure: FFmpegRuntimeFailure?,
                ) {
                    if (failure?.requestId == REPLACED_REQUEST_ID) {
                        oldFailureCode.set(failure.failureCodeWireValue)
                        oldTerminalCount.incrementAndGet()
                        oldTerminalSequence.set(eventSequence)
                        oldTerminal.countDown()
                    }
                }
            }
        val newCallback =
            object : IFFmpegRuntimeCallback.Stub() {
                override fun onRequestStarted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    requestId: String?,
                    sessionId: Long,
                    processId: Int,
                ) = Unit

                override fun onRequestCompleted(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    result: FFmpegRuntimeResult?,
                ) {
                    if (result?.requestId == REPLACED_REQUEST_ID) {
                        newOwnerOldRequestTerminalCount.incrementAndGet()
                    }
                }

                override fun onRequestFailed(
                    runtimeGeneration: Long,
                    eventSequence: Long,
                    failure: FFmpegRuntimeFailure?,
                ) {
                    if (failure?.requestId == REPLACED_REQUEST_ID) {
                        newOwnerOldRequestTerminalCount.incrementAndGet()
                    }
                }
            }
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    runtime = IFFmpegRuntime.Stub.asInterface(service)
                    connected.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }

        assertTrue(
            context.bindService(
                Intent(context, FFmpegRuntimeService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            ),
        )
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val activeRuntime = requireNotNull(runtime)
            assertTrue(activeRuntime.registerCallback(RUNTIME_GENERATION, oldCallback))
            assertTrue(
                activeRuntime.submit(
                    RUNTIME_GENERATION,
                    argumentRequest(
                        REPLACED_REQUEST_ID,
                        listOf(
                            "-re",
                            "-f",
                            "lavfi",
                            "-i",
                            "testsrc=size=16x16:rate=1",
                            "-t",
                            "30",
                            "-f",
                            "null",
                            "-",
                        ),
                    ),
                ),
            )
            assertTrue(oldStarted.await(10, TimeUnit.SECONDS))

            assertTrue(activeRuntime.registerCallback(REPLACEMENT_GENERATION, newCallback))
            assertTrue(oldTerminal.await(10, TimeUnit.SECONDS))
            Thread.sleep(1_000)

            assertEquals(1, oldTerminalCount.get())
            assertEquals(
                FFmpegRuntimeFailureCode.CALLBACK_REPLACED.wireValue,
                oldFailureCode.get(),
            )
            assertTrue(oldTerminalSequence.get() > oldStartedSequence.get())
            assertEquals(0, newOwnerOldRequestTerminalCount.get())
        } finally {
            context.unbindService(connection)
        }
    }

    private fun commandRequest(requestId: String, command: String): FFmpegRuntimeRequest =
        FFmpegRuntimeRequest(
            requestId = requestId,
            operationWireValue = FFmpegRuntimeOperation.EXECUTE_COMMAND.wireValue,
            command = command,
        )

    private fun argumentRequest(
        requestId: String,
        arguments: List<String>,
    ): FFmpegRuntimeRequest =
        FFmpegRuntimeRequest(
            requestId = requestId,
            operationWireValue = FFmpegRuntimeOperation.EXECUTE_ARGUMENTS.wireValue,
            arguments = arguments,
        )

    private companion object {
        const val RUNTIME_GENERATION = 1L
        const val REPLACEMENT_GENERATION = 2L
        const val INFO_REQUEST_ID = "11111111111111111111111111111111"
        const val REPLACED_REQUEST_ID = "44444444444444444444444444444444"
        const val QUEUE_BLOCKER_REQUEST_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val QUEUED_CANCEL_REQUEST_ID = "cccccccccccccccccccccccccccccccc"
        val CONCURRENT_REQUEST_IDS =
            listOf(
                "22222222222222222222222222222222",
                "33333333333333333333333333333333",
            )
    }
}
