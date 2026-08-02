package com.kiyori.platform

import android.content.Context
import com.kiyori.platform.android.ApplicationContextAccess
import com.kiyori.platform.lifecycle.ApplicationStartupTime
import com.kiyori.platform.lifecycle.MainApplicationInitialization
import com.kiyori.platform.serialization.ApplicationJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ApplicationPlatformContractTest {
    @Test
    fun processContractsExposeOnlyTheInstalledValues() {
        val sourceContext = mock<Context>()
        val applicationContext = mock<Context>()
        whenever(sourceContext.applicationContext).thenReturn(applicationContext)

        ApplicationContextAccess.installForProcess(sourceContext)
        assertSame(applicationContext, ApplicationContextAccess.current)

        val json = Json { ignoreUnknownKeys = true }
        ApplicationJson.installForProcess(json)
        assertSame(json, ApplicationJson.current)

        assertEquals(0L, ApplicationStartupTime.epochMillis)
        ApplicationStartupTime.recordForProcess(1234L)
        assertEquals(1234L, ApplicationStartupTime.epochMillis)
    }

    @Test
    fun mainInitializationContractKeepsTheTwoRequestsDistinct() {
        val calls = mutableListOf<String>()
        val initialization =
            object : MainApplicationInitialization {
                override fun initializeMainUiPrerequisites() {
                    calls += "ui"
                }

                override fun initializeMainApplication() {
                    calls += "main"
                }
            }

        initialization.initializeMainUiPrerequisites()
        initialization.initializeMainApplication()

        assertEquals(listOf("ui", "main"), calls)
    }
}
