package de.nicerdicer.functions

import de.nicerdicer.util.RollResult
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.string

object LegacyRollFunction : FunctionBase("r", "Roll function via a string.")
{
    override suspend fun prepare(kord: Kord)
    {
        kord.createGlobalChatInputCommand(name, description) {
            string("roll_string", "What to roll. Format: 3d20+4") {
                required = false
            }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()
        val rs = event.interaction.command.strings["roll_string"] ?: "3d20+4"
        val (rollString, rollTimes) = parseLegacyMultiroll(rs) ?: run {
            response.respond { content = "Multiroll count must be between 1 and 20." }
            return
        }

        val sb = StringBuilder()
        repeat(rollTimes)
        {
            val result = RollResult(rollString, 20, 3, 4, parseLegacyShorthand = true)
            if (!result.roll())
            {
                response.respond { content = "Dice type and amount have to be greater than 0!" }
                return
            }

            if (rollTimes > 1) sb.append("Roll ${it + 1}: ")
            sb.append(result.getRollString()).append("\n")
        }

        response.respond {
            content = sb.toString().trimEnd()
        }
    }
}

/** Splits the legacy `/r` multiroll suffix from the dice text so it can be validated independently. */
internal fun parseLegacyMultiroll(rollString: String): Pair<String, Int>?
{
    val suffix = Regex("\\s*x(\\d+)\\s*$", RegexOption.IGNORE_CASE).find(rollString) ?: return Pair(rollString, 1)
    val rollTimes = suffix.groupValues[1].toIntOrNull() ?: return null
    if (rollTimes !in 1..20) return null

    return Pair(rollString.removeRange(suffix.range).trim(), rollTimes)
}
