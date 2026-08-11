package gobby.utils.managers

import gobby.utils.managers.SwapManager.SendDecision
import kotlin.test.Test
import kotlin.test.assertEquals

class SwapManagerTest {

    @Test
    fun firstSwapOfTickIsAccepted() {
        assertEquals(SendDecision.ACCEPT, SwapManager.decideSend(slot = 4, serverSlot = 3, swappedThisTick = false))
    }

    @Test
    fun swapToCurrentServerSlotIsRedundant() {
        assertEquals(SendDecision.CANCEL_REDUNDANT, SwapManager.decideSend(slot = 3, serverSlot = 3, swappedThisTick = false))
    }

    @Test
    fun secondSwapInSameTickIsBlockedAndRolledBack() {
        assertEquals(SendDecision.CANCEL_ROLLBACK, SwapManager.decideSend(slot = 5, serverSlot = 3, swappedThisTick = true))
    }

    @Test
    fun redundantTakesPriorityOverRollback() {
        assertEquals(SendDecision.CANCEL_REDUNDANT, SwapManager.decideSend(slot = 3, serverSlot = 3, swappedThisTick = true))
    }
}
