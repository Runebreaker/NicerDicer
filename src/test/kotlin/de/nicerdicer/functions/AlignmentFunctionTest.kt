package de.nicerdicer.functions

import kotlin.test.Test
import kotlin.test.assertEquals

class AlignmentFunctionTest
{
    /** Verifies the stored D&D axes always produce the intended Discord role names. */
    @Test
    fun mapsTheNineDndAlignmentsToRoleNames()
    {
        assertEquals("Lawful Good", AlignmentFunction.alignmentRoleName("Lawful", "Good"))
        assertEquals("Lawful Neutral", AlignmentFunction.alignmentRoleName("Lawful", "Neutral"))
        assertEquals("Lawful Evil", AlignmentFunction.alignmentRoleName("Lawful", "Evil"))
        assertEquals("Neutral Good", AlignmentFunction.alignmentRoleName("Neutral", "Good"))
        assertEquals("True Neutral", AlignmentFunction.alignmentRoleName("Neutral", "Neutral"))
        assertEquals("Neutral Evil", AlignmentFunction.alignmentRoleName("Neutral", "Evil"))
        assertEquals("Chaotic Good", AlignmentFunction.alignmentRoleName("Chaotic", "Good"))
        assertEquals("Chaotic Neutral", AlignmentFunction.alignmentRoleName("Chaotic", "Neutral"))
        assertEquals("Chaotic Evil", AlignmentFunction.alignmentRoleName("Chaotic", "Evil"))
    }
}
