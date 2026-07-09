package de.nicerdicer.functions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyRollFunctionTest
{
    /** Verifies `/r` accepts a trailing multiroll suffix without changing the dice request. */
    @Test
    fun parsesLegacyMultirollSuffixes()
    {
        assertEquals(Pair("", 2), parseLegacyMultiroll("x2"))
        assertEquals(Pair("2d20+6", 2), parseLegacyMultiroll("2d20+6x2"))
        assertEquals(Pair("2d20+6", 2), parseLegacyMultiroll("2d20+6 x2"))
        assertEquals(Pair("2d20+6", 2), parseLegacyMultiroll("2d20+6X2"))
    }

    /** Verifies malformed multiroll counts are rejected before roll results are generated. */
    @Test
    fun rejectsOutOfRangeLegacyMultirollSuffixes()
    {
        assertNull(parseLegacyMultiroll("x0"))
        assertNull(parseLegacyMultiroll("x21"))
        assertNull(parseLegacyMultiroll("x999999999999999999999"))
    }
}
