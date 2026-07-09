package de.nicerdicer.functions

import de.nicerdicer.util.RollResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ConcFunctionTest
{
    /** Verifies `/c` applies the requested concentration defaults and shorthand modifiers. */
    @Test
    fun parsesConcentrationRollDefaultsAndModifiers()
    {
        val cases = listOf(
            Pair("", 5),
            Pair("+1", 1),
            Pair("++1", 6),
            Pair("--1", 4),
        )

        cases.forEach { (rollString, expectedModifier) ->
            val result = RollResult(rollString, 10, 1, 5, parseLegacyShorthand = true)

            assertEquals(10, result.diceType)
            assertEquals(1, result.amount)
            assertEquals(expectedModifier, result.modifier, "Unexpected modifier for '$rollString'")
        }
    }
}
