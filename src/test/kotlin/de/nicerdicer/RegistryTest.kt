package de.nicerdicer

import kotlin.test.Test
import kotlin.test.assertEquals

class RegistryTest
{
    /** Ensures every feature in the explicit catalogue has a unique command name. */
    @Test
    fun moduleCatalogHasUniqueCommandNames()
    {
        val commandNames = Registry.commands.map { it.name }

        assertEquals(commandNames.size, commandNames.distinct().size)
    }

    /** Ensures synchronization deletes only commands removed from the catalogue. */
    @Test
    fun commandNamesToRemoveKeepsDeclaredCommands()
    {
        val obsoleteNames = commandNamesToRemove(
            existingNames = listOf("roll", "tag", "obsolete"),
            declaredNames = listOf("roll", "tag", "combat"),
        )

        assertEquals(setOf("obsolete"), obsoleteNames)
    }

    /** Ensures an unchanged catalogue does not schedule any Discord command deletion. */
    @Test
    fun commandNamesToRemoveReturnsNothingWhenCatalogMatchesDiscord()
    {
        val obsoleteNames = commandNamesToRemove(
            existingNames = listOf("roll", "tag"),
            declaredNames = listOf("roll", "tag"),
        )

        assertEquals(emptySet(), obsoleteNames)
    }
}
