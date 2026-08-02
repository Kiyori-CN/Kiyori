package com.kiyori.platform

import android.app.Application
import com.kiyori.platform.android.ApplicationContextAccess
import com.kiyori.platform.lifecycle.ApplicationStartupTime
import com.kiyori.platform.lifecycle.MainApplicationInitialization
import com.kiyori.platform.serialization.ApplicationJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class ApplicationPlatformContractTest {
    @Test
    fun processContractsExposeOnlyTheInstalledValues() {
        val application = mock<Application>()

        ApplicationContextAccess.installForProcess(application)
        assertSame(application, ApplicationContextAccess.current)

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
