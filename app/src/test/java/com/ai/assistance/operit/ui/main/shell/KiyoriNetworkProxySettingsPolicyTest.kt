package com.ai.assistance.operit.ui.main.shell

import com.kiyori.platform.network.MihomoNodeTestResult
import com.kiyori.platform.network.MihomoNodeTestStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class KiyoriNetworkProxySettingsPolicyTest {
    @Test
    fun `node page uses final title and two header action slots`() {
        assertEquals("节点选择", NETWORK_PROXY_NODE_SELECTION_TITLE)
        assertEquals(96, NETWORK_PROXY_HEADER_ACTION_WIDTH_DP)
    }

    @Test
    fun `default order keeps subscription order`() {
        val nodes = listOf(node("Beta"), node("Alpha"), node("Gamma"))

        assertEquals(nodes, sortNetworkProxyNodes(nodes, NetworkProxyNodeSort.DEFAULT))
    }

    @Test
    fun `name order is case insensitive and stable for equal names`() {
        val nodes = listOf(node("zeta"), node("Alpha"), node("alpha"), node("Beta"))

        assertEquals(
            listOf("Alpha", "alpha", "Beta", "zeta"),
            sortNetworkProxyNodes(nodes, NetworkProxyNodeSort.NAME).map(MihomoNodeTestResult::name),
        )
    }

    @Test
    fun `delay order puts measured nodes first and failures last`() {
        val nodes =
            listOf(
                node("Timeout", status = MihomoNodeTestStatus.TIMEOUT),
                node("Slow", delay = 420),
                node("Untested"),
                node("Fast", delay = 80),
            )

        assertEquals(
            listOf("Fast", "Slow", "Timeout", "Untested"),
            sortNetworkProxyNodes(nodes, NetworkProxyNodeSort.DELAY).map(MihomoNodeTestResult::name),
        )
    }

    private fun node(
        name: String,
        delay: Int? = null,
        status: MihomoNodeTestStatus = MihomoNodeTestStatus.UNTESTED,
    ): MihomoNodeTestResult =
        MihomoNodeTestResult(name = name, type = "节点", delayMillis = delay, status = status)
            .let { result ->
                if (delay == null && status == MihomoNodeTestStatus.UNTESTED) result
                else result.copy(status = status.takeIf { it != MihomoNodeTestStatus.UNTESTED } ?: MihomoNodeTestStatus.SUCCESS)
            }
}
