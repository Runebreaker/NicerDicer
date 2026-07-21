package de.nicerdicer.functions

import de.nicerdicer.util.RollResult
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string

object ConcFunction : FunctionBase("c", "Roll a concentration check.")
{
    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {
        builder.apply {
            string("roll_string", "Optional modifier, such as +1, ++1, or --1") {
                required = false
            }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()
        val rollString = event.interaction.command.strings["roll_string"].orEmpty()
        val result = RollResult(rollString, 10, 1, 5, parseLegacyShorthand = true)

        if (!result.roll())
        {
            response.respond { content = "Dice type and amount have to be greater than 0!" }
            return
        }

        response.respond { content = result.getRollString() }
    }
}
