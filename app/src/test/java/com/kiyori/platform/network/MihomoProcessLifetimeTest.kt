package com.kiyori.platform.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class MihomoProcessLifetimeTest {
    @Test
    fun `creator survives caller exit and releases after process exits`() {
        val process = ControlledProcess()
        val creator = AtomicReference<Thread>()
        val result = AtomicReference<Process>()
        val caller = thread {
            result.set(startMihomoParentBoundProcess {
                creator.set(Thread.currentThread())
                process
            })
        }
        caller.join(3000)
        assertFalse(caller.isAlive)
        assertSame(process, result.get())
        assertTrue(process.waiting.await(3, TimeUnit.SECONDS))
        try {
            assertTrue(creator.get().isAlive)
            assertNotSame(caller, creator.get())
            // 线程中断不能等价于用户停止代理。
            creator.get().interrupt()
            assertTrue(process.interrupted.await(3, TimeUnit.SECONDS))
            assertTrue(creator.get().isAlive)
            assertTrue(process.isAlive)
        } finally {
            process.destroy()
            creator.get().join(3000)
        }
        assertFalse(creator.get().isAlive)
    }

    @Test
    fun `launch failure preserves original exception and releases creator`() {
        val creator = AtomicReference<Thread>()
        val failure = IOException("test executable missing")
        val actual = assertThrows(IOException::class.java) {
            startMihomoParentBoundProcess {
                creator.set(Thread.currentThread())
                throw failure
            }
        }
        assertSame(failure, actual)
        creator.get().join(3000)
        assertFalse(creator.get().isAlive)
    }

    @Test
    fun `interrupted caller still receives process handle and retains interrupt`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val creator = AtomicReference<Thread>()
        val result = AtomicReference<Process>()
        val wasInterrupted = AtomicReference<Boolean>()
        val process = ControlledProcess()
        val caller = thread {
            result.set(startMihomoParentBoundProcess {
                creator.set(Thread.currentThread())
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
                process
            })
            wasInterrupted.set(Thread.currentThread().isInterrupted)
        }
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            caller.interrupt()
            release.countDown()
            caller.join(3000)
            assertFalse(caller.isAlive)
            assertSame(process, result.get())
            assertEquals(true, wasInterrupted.get())
        } finally {
            release.countDown()
            process.destroy()
            creator.get()?.join(3000)
        }
    }

    @Test
    fun `immediate process exit does not strand startup`() {
        val process = ControlledProcess().apply { destroy() }
        val creator = AtomicReference<Thread>()
        assertSame(process, startMihomoParentBoundProcess {
            creator.set(Thread.currentThread())
            process
        })
        creator.get().join(3000)
        assertFalse(creator.get().isAlive)
        assertEquals(0, process.exitValue())
    }

    private class ControlledProcess : Process() {
        val waiting = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        private val exited = CountDownLatch(1)
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int {
            waiting.countDown()
            try {
                exited.await()
            } catch (error: InterruptedException) {
                interrupted.countDown()
                throw error
            }
            return 0
        }
        override fun exitValue(): Int {
            if (isAlive) throw IllegalThreadStateException()
            return 0
        }
        override fun isAlive() = exited.count != 0L
        override fun destroy() { exited.countDown() }
    }
}
