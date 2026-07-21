package de.nicerdicer.functions

import de.nicerdicer.db.Database
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string

object QuickTagFunction : FunctionBase("t", "Gets a tag!")
{
    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {
        builder.apply {
            string("tag_name", "The name of the tag to get") {
                required = true
            }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()
        try {
            val name = event.interaction.command.strings["tag_name"]?.trim().orEmpty()
            if (name.isBlank()) {
                response.respond { content = "Usage: /t <tag_name>" }
                return
            }

            Database.init()

            val tag = try {
                Database.getTag(name)
            } catch (e: Exception) {
                println("QuickTagFunction.execute: Database.getTag failed for '$name': ${e.message}")
                e.printStackTrace()
                null
            }

            if (tag == null) {
                response.respond { content = "Tag '$name' not found." }
            } else {
                response.respond { content = tag.content }
            }
        } catch (e: Exception) {
            println("QuickTagFunction.execute: unexpected error: ${e.message}")
            e.printStackTrace()
            try {
                response.respond { content = "An internal error occurred while fetching the tag." }
            } catch (_: Exception) { /* ignore */ }
        }
    }
}