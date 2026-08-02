package com.kiyori.app.startup

import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KiyoriMainStartupGateCoordinatorTest {
    @Test
    fun `agreement has precedence over the permission guide`() {
        assertEquals(
            KiyoriMainStartupDestination.AGREEMENT,
            resolveKiyoriMainStartupDestination(
                agreementAccepted = false,
                showPermissionGuide = true,
            ),
        )
    }

    @Test
    fun `accepted agreement with missing permission level shows the guide`() {
        assertEquals(
            KiyoriMainStartupDestination.PERMISSION_GUIDE,
            resolveKiyoriMainStartupDestination(
                agreementAccepted = true,
                showPermissionGuide = true,
            ),
        )
    }

    @Test
    fun `accepted agreement and configured permission level show content`() {
        assertEquals(
            KiyoriMainStartupDestination.CONTENT,
            resolveKiyoriMainStartupDestination(
                agreementAccepted = true,
                showPermissionGuide = false,
            ),
        )
    }

    @Test
    fun `accepting the agreement delegates to the persistent owner`() {
        var agreementAccepted = false
        val coordinator =
            coordinator(
                isAgreementAccepted = { agreementAccepted },
                acceptCurrentAgreement = { agreementAccepted = true },
                readPermissionLevel = { AndroidPermissionLevel.STANDARD },
            )

        assertEquals(KiyoriMainStartupDestination.AGREEMENT, coordinator.destination)

        coordinator.acceptCurrentAgreement()

        assertEquals(KiyoriMainStartupDestination.CONTENT, coordinator.destination)
    }

    @Test
    fun `missing permission level updates only the guide projection`() {
        val logs = mutableListOf<String>()
        val coordinator =
            coordinator(
                readPermissionLevel = { null },
                logger = { _, message -> logs += message },
            )

        coordinator.refreshPermissionLevel()

        assertEquals(KiyoriMainStartupDestination.PERMISSION_GUIDE, coordinator.destination)
        assertFalse(coordinator.isReadyForContent)
        assertEquals(
            listOf(
                "当前权限级别: null",
                "权限级别检查: 已设置=false, 将显示权限引导界面",
            ),
            logs,
        )
    }

    @Test
    fun `configured permission level keeps content ready`() {
        val logs = mutableListOf<String>()
        val coordinator =
            coordinator(
                readPermissionLevel = { AndroidPermissionLevel.STANDARD },
                logger = { _, message -> logs += message },
            )

        coordinator.refreshPermissionLevel()

        assertEquals(KiyoriMainStartupDestination.CONTENT, coordinator.destination)
        assertTrue(coordinator.isReadyForContent)
        assertEquals(
            listOf(
                "当前权限级别: STANDARD",
                "权限级别检查: 已设置=true, 将不显示权限引导界面",
            ),
            logs,
        )
    }

    @Test
    fun `completing the permission guide clears its ui projection`() {
        val coordinator =
            coordinator(
                readPermissionLevel = { null },
            )
        coordinator.refreshPermissionLevel()
        assertEquals(KiyoriMainStartupDestination.PERMISSION_GUIDE, coordinator.destination)

        coordinator.completePermissionGuide()

        assertEquals(KiyoriMainStartupDestination.CONTENT, coordinator.destination)
    }

    private fun coordinator(
        isAgreementAccepted: () -> Boolean = { true },
        acceptCurrentAgreement: () -> Unit = {},
        readPermissionLevel: () -> AndroidPermissionLevel?,
        logger: (tag: String, message: String) -> Unit = { _, _ -> },
    ): KiyoriMainStartupGateCoordinator =
        KiyoriMainStartupGateCoordinator(
            isAgreementAccepted = isAgreementAccepted,
            acceptCurrentAgreement = acceptCurrentAgreement,
            readPermissionLevel = readPermissionLevel,
            logger = logger,
        )
}
