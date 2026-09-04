package com.ai.assistance.operit.util

import java.io.File
import java.net.Proxy
import java.net.URL
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BilibiliMediaLiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun explicitMediaSamplesUseTheProductionRangeDownloader() {
        val input = System.getenv("KIYORI_BILIBILI_MEDIA_INPUT")
        assumeTrue("No explicit live media input", input != null)
        val samples = JSONArray(File(requireNotNull(input)).readText())
        assertTrue(samples.length() > 0)
        val tested = mutableSetOf<String>()
        for (index in 0 until samples.length()) {
            val sample = samples.getJSONObject(index)
            val url = URL(sample.getString("url"))
            require(url.protocol == "https" && url.userInfo == null &&
                Regex("/(v1/resource/)?upgcxcode/.*").matches(url.path))
            if (!tested.add(url.toString())) continue
            val values = sample.getJSONObject("headers")
            val headers = values.keys().asSequence().associateWith { values.getString(it) }
            require(headers.keys.none { it.equals("Cookie", true) })
            val destination = temporaryFolder.newFile("sample-$index.bin")
            HttpMultiPartDownloader.downloadSegment(url.toString(), destination, headers,
                { it.openConnection(Proxy.NO_PROXY) }, startInclusive = 0, endInclusive = 1023)
            assertEquals(1024L, destination.length())
        }
    }
}
