package de.nicerdicer.functions

import de.nicerdicer.db.Database
import de.nicerdicer.util.bold
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.integer

object ReputationFunction : FunctionBase("rep", "Everything to do with reputation!")
{
    override suspend fun prepare(kord: Kord)
    {
        kord.createGlobalChatInputCommand(name, description) {
            integer("amount", "The amount of rep to add! Can be negative.") { required = false }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()
        val integer = event.interaction.command.integers["amount"]?.toInt()

        val guildId = event.interaction.data.guildId.value?.toString()
        val userId = event.interaction.user.id.toString()

        if (guildId == null)
        {
            response.respond { content = "This command can only be used in a guild." }
            return
        }

        try
        {
            // Add to Reputation, if integer given
            integer?.let {
                if (Database.addToReputation(guildId, userId, integer))
                {
                    response.respond { content = "Reputation updated!" }
                    return
                } else
                {
                    response.respond { content = "Failed to update reputation." }
                    return
                }
            }

            // Show reputation otherwise
            val reputation = Database.getReputation(guildId, userId)

            response.respond {
                content = "Your reputation is: ${(reputation?.amount ?: 0).toString().bold()}"
            }
        } catch (e: Exception)
        {
            println("ReputationFunction.execute: unexpected error: ${e.message}")
            e.printStackTrace()
            response.respond { content = "An internal error occurred while handling the reputation command." }
        }
    }
}