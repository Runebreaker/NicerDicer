package de.nicerdicer.util

import kotlin.test.Test
import kotlin.test.assertEquals

class RollResultTest
{
    /** Verifies the shorthand forms accepted by the legacy `/r` command. */
    @Test
    fun parsesLegacyRollDefaultsAndModifierShortcuts()
    {
        val cases = listOf(
            Triple("", 3, 4),
            Triple("1", 1, 4),
            Triple("+1", 3, 1),
            Triple("++1", 3, 5),
            Triple("--1", 3, 3),
            Triple("1+1", 1, 1),
            Triple("1++1", 1, 5),
        )

        cases.forEach { (rollString, expectedAmount, expectedModifier) ->
            val result = RollResult(rollString, 20, 3, 4, parseLegacyShorthand = true)

            assertEquals(expectedAmount, result.amount, "Unexpected amount for '$rollString'")
            assertEquals(20, result.diceType, "Unexpected die type for '$rollString'")
            assertEquals(expectedModifier, result.modifier, "Unexpected modifier for '$rollString'")
        }
    }

    /** Ensures legacy shorthand does not alter unrelated roll-string consumers. */
    @Test
    fun keepsLegacyShorthandOptIn()
    {
        val result = RollResult("++1", 20, 1, 4)

        assertEquals(1, result.amount)
        assertEquals(1, result.modifier)
    }

    /** Protects explicit non-d20 rolls from the legacy shorthand changes. */
    @Test
    fun preservesExplicitDiceSpecifications()
    {
        val result = RollResult("2d8+0", 20, 3, 4, parseLegacyShorthand = true)

        assertEquals(2, result.amount)
        assertEquals(8, result.diceType)
        assertEquals(0, result.modifier)
    }
}
