package de.nicerdicer.functions

import de.nicerdicer.db.TagEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagFunctionTest
{
    @Test
    fun inlineEditDoesNotOpenModalWhenNameAndContentAreProvided()
    {
        assertFalse(TagFunction.shouldOpenEditModal("rules", "Updated content"))
    }

    @Test
    fun editOpensModalWhenContentIsMissing()
    {
        assertTrue(TagFunction.shouldOpenEditModal("rules", ""))
    }

    @Test
    fun editOpensModalWhenNameIsMissing()
    {
        assertTrue(TagFunction.shouldOpenEditModal("", "Updated content"))
    }

    @Test
    fun modalDefaultsAreBlankWhenNoTagNameWasProvided()
    {
        val defaults = TagFunction.resolveEditModalDefaults("", "", "user-1", null)

        assertEquals(TagFunction.EditTagModalDefaults("", ""), defaults)
    }

    @Test
    fun modalDefaultsKeepPartialContentWhenNoTagNameWasProvided()
    {
        val defaults = TagFunction.resolveEditModalDefaults("", "Draft content", "user-1", null)

        assertEquals(TagFunction.EditTagModalDefaults("", "Draft content"), defaults)
    }

    @Test
    fun modalDefaultsUseExistingOwnedTag()
    {
        val tag = TagEntry(name = "rules", owner = "user-1", content = "Current content")

        val defaults = TagFunction.resolveEditModalDefaults("rules", "", "user-1", tag)

        assertEquals(TagFunction.EditTagModalDefaults("rules", "Current content"), defaults)
    }

    @Test
    fun modalDoesNotOpenForMissingNamedTag()
    {
        assertNull(TagFunction.resolveEditModalDefaults("missing", "", "user-1", null))
    }

    @Test
    fun modalDoesNotOpenForTagOwnedByAnotherUser()
    {
        val tag = TagEntry(name = "rules", owner = "user-2", content = "Current content")

        assertNull(TagFunction.resolveEditModalDefaults("rules", "", "user-1", tag))
    }
}
