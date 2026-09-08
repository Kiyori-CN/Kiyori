package com.ai.assistance.operit.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class AppDatabaseSingletonTest {
    @Before
    fun resetDatabase() = AppDatabase.closeDatabase()

    @After
    fun closeDatabase() = AppDatabase.closeDatabase()

    @Test
    fun concurrentFirstCallersShareTheDatabaseAndItsTransactionOwner() {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        val firstDatabase = Mockito.mock(AppDatabase::class.java)
        val secondDatabase = Mockito.mock(AppDatabase::class.java)
        val builder = mock<RoomDatabase.Builder<AppDatabase>>(defaultAnswer = Mockito.RETURNS_SELF)
        val buildCount = AtomicInteger()
        val firstBuildEntered = CountDownLatch(1)
        val releaseFirstBuild = CountDownLatch(1)
        Mockito.`when`(builder.build()).thenAnswer {
            if (buildCount.incrementAndGet() == 1) {
                firstBuildEntered.countDown()
                check(releaseFirstBuild.await(10, TimeUnit.SECONDS))
                firstDatabase
            } else {
                secondDatabase
            }
        }
        val secondCallerReady = CountDownLatch(1)
        val secondThread = AtomicReference<Thread>()
        val executor = Executors.newFixedThreadPool(2)
        fun caller(second: Boolean) = Callable {
            // Mockito 的静态替身只属于当前线程；实际单例入口和 monitor 使用生产实现。
            Mockito.mockStatic(Room::class.java).use { room ->
                room.`when`<RoomDatabase.Builder<AppDatabase>> {
                    Room.databaseBuilder(context, AppDatabase::class.java, "app_database")
                }.thenReturn(builder)
                if (second) {
                    secondThread.set(Thread.currentThread())
                    secondCallerReady.countDown()
                }
                AppDatabase.getDatabase(context)
            }
        }
        try {
            val first = executor.submit(caller(false))
            assertTrue("First caller must hold the initialization monitor", firstBuildEntered.await(10, TimeUnit.SECONDS))
            val second = executor.submit(caller(true))
            assertTrue(secondCallerReady.await(10, TimeUnit.SECONDS))
            // 在第一次发布 INSTANCE 前，确保第二个调用已读到 null 并排队等待同一把锁。
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (secondThread.get().state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertEquals(Thread.State.BLOCKED, secondThread.get().state)
            releaseFirstBuild.countDown()
            val firstResult = first.get(10, TimeUnit.SECONDS)
            val secondResult = second.get(10, TimeUnit.SECONDS)
            assertSame("Chat DAO and audit transaction must share one Room instance", firstResult, secondResult)
            assertSame(firstResult, AppDatabase.getDatabase(context))
            assertEquals(1, buildCount.get())
        } finally {
            releaseFirstBuild.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
