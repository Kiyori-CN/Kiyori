package com.ai.assistance.operit.ui.features.packages.market

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Comment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MarketCommentSubmissionTest {
    private fun controller(scope: CoroutineScope, api: MarketStatsApiService, errors: MutableList<String>) =
        MarketInteractionController(scope, mock<Context>(), api, mock<GitHubApiService>(), "MarketTest",
            errors::add, MarketInteractionMessages({ it }, { it }, { it }, { it }, { it }, { it }))

    @Test fun `pending authorization prevents duplicate sends and only success closes draft`() = runTest {
        mockStatic(Log::class.java).use {
            val api = mock<MarketStatsApiService>()
            whenever(api.postComment("entry", "draft", null)).thenReturn(Result.success(MarketV2Comment(id = "comment", body = "draft")))
            val errors = mutableListOf<String>()
            val subject = controller(this, api, errors)
            val authorized = CompletableDeferred<Unit>()
            var closes = 0
            subject.postEntryComment("entry", "draft", authorize = { authorized.await() }, onSuccess = { closes++ })
            subject.postEntryComment("entry", "draft", onSuccess = { closes++ })
            runCurrent()
            assertEquals(setOf("entry"), subject.isPostingComment.value)
            assertEquals(0, closes)
            authorized.complete(Unit)
            runCurrent()
            verify(api, times(1)).postComment("entry", "draft", null)
            assertEquals(1, closes)
            assertTrue(subject.isPostingComment.value.isEmpty())
            assertTrue(errors.isEmpty())
        }
    }

    @Test fun `failed post retains draft and can be explicitly retried`() = runTest {
        mockStatic(Log::class.java).use {
            val api = mock<MarketStatsApiService>()
            whenever(api.postComment("entry", "draft", null)).thenReturn(Result.failure(IllegalStateException("offline")))
            val errors = mutableListOf<String>()
            val subject = controller(this, api, errors)
            var closes = 0
            repeat(2) {
                subject.postEntryComment("entry", "draft", onSuccess = { closes++ })
                runCurrent()
            }
            assertEquals(0, closes)
            assertEquals(listOf("offline", "offline"), errors)
            assertTrue(subject.isPostingComment.value.isEmpty())
            verify(api, times(2)).postComment("entry", "draft", null)
        }
    }

    @Test fun `edit reports failure without publishing new body or closing editor`() = runTest {
        mockStatic(Log::class.java).use {
            val api = mock<MarketStatsApiService>()
            whenever(api.getComments("entry")).thenReturn(Result.success(listOf(MarketV2Comment(id = "comment", body = "old"))))
            whenever(api.editComment("comment", "new")).thenReturn(Result.failure(IllegalStateException("offline")))
            val errors = mutableListOf<String>()
            val subject = controller(this, api, errors)
            subject.loadEntryComments("entry")
            runCurrent()
            var closed = false
            subject.editEntryComment("entry", "comment", "new", onSuccess = { closed = true })
            assertTrue(subject.isPostingComment.value.contains("entry"))
            runCurrent()
            assertFalse(closed)
            assertEquals("old", subject.entryComments.value["entry"]!!.single().body)
            assertTrue(subject.isPostingComment.value.isEmpty())
            assertEquals(1, errors.size)
        }
    }

    @Test fun `already cancelled owner does not leave a pending submission`() = runTest {
        val job = Job().apply { cancel() }
        val subject = controller(CoroutineScope(coroutineContext + job), mock(), mutableListOf())
        subject.postEntryComment("entry", "draft")
        runCurrent()
        assertTrue(subject.isPostingComment.value.isEmpty())
    }
}
