package gobby.pathfinder.etherwarp

import gobby.pathfinder.etherwarp.EtherwarpPathExecutor.Decision
import kotlin.test.Test
import kotlin.test.assertEquals

class EtherwarpExecutorDecisionTest {

    private fun decide(
        holdingItem: Boolean = true,
        swapPossible: Boolean = true,
        sneakReady: Boolean = true,
        aimValid: Boolean = true
    ) = EtherwarpPathExecutor.decide(holdingItem, swapPossible, sneakReady, aimValid)

    @Test
    fun cancelsWhenItemMissingAndNotSwappable() {
        assertEquals(Decision.CANCEL, decide(holdingItem = false, swapPossible = false))
    }

    @Test
    fun swapsWhenItemMissingButAvailable() {
        assertEquals(Decision.SWAP, decide(holdingItem = false, swapPossible = true))
    }

    @Test
    fun waitsUntilSneakConfirmed() {
        assertEquals(Decision.WAIT, decide(sneakReady = false))
    }

    @Test
    fun cancelsWhenAimUnreachable() {
        assertEquals(Decision.CANCEL, decide(sneakReady = true, aimValid = false))
    }

    @Test
    fun castsWhenReadyAndAimValid() {
        assertEquals(Decision.CAST, decide(sneakReady = true, aimValid = true))
    }
}
