package de.nicerdicer.db

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseTagTest
{
    @Test
    fun tagContentCanOnlyBeUpdatedByOwner()
    {
        val dbDir = Path.of("db")
        val dbFile = dbDir.resolve("nicerdicer.db")
        val dbDirExisted = Files.exists(dbDir)
        val dbFileExisted = Files.exists(dbFile)
        val tagName = "__codex_test_${UUID.randomUUID()}"
        val ownerId = "test-owner-${UUID.randomUUID()}"
        val otherUserId = "test-other-${UUID.randomUUID()}"

        Files.createDirectories(dbDir)

        try
        {
            assertTrue(Database.createTag(tagName, ownerId, "original content"))
            assertFalse(Database.updateTag(tagName, otherUserId, "wrong owner update"))
            assertEquals("original content", assertNotNull(Database.getTag(tagName)).content)

            assertTrue(Database.updateTag(tagName, ownerId, "updated content"))
            assertEquals("updated content", assertNotNull(Database.getTag(tagName)).content)
        } finally
        {
            Database.deleteTag(tagName, ownerId)
            resetDatabaseInitialization()
            if (!dbFileExisted) Files.deleteIfExists(dbFile)
            if (!dbDirExisted) Files.deleteIfExists(dbDir)
        }
    }

    private fun resetDatabaseInitialization()
    {
        val initializedField = Database::class.java.getDeclaredField("initialized")
        initializedField.isAccessible = true
        initializedField.setBoolean(Database, false)
    }
}
