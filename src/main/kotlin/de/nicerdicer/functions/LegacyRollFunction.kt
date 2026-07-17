package de.nicerdicer.functions

import de.nicerdicer.util.RollResult
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.string

object LegacyRollFunction : FunctionBase("r", "Roll function via a string.")
{
    override fun register(registrar: InteractionRegistrar)
    {
        registrar.command(name, description, ::execute) {
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
            response.respond { content = "Multiroll count must be between 1 and 100." }
            return
        }

        val resultPages = mutableListOf<String>()
        var resultPage = StringBuilder()
        repeat(rollTimes)
        {
            val result = RollResult(rollString, 20, 3, 4, parseLegacyShorthand = true)
            if (!result.roll())
            {
                response.respond { content = "Dice type and amount have to be greater than 0!" }
                return
            }

            val line = if (rollTimes > 1) "Roll ${it + 1}: ${result.getRollString()}" else result.getRollString()
            if (resultPage.isNotEmpty() && resultPage.length + line.length + 1 > 2_000)
            {
                resultPages.add(resultPage.toString().trimEnd())
                resultPage = StringBuilder()
            }
            resultPage.append(line).append("\n")
        }
        resultPages.add(resultPage.toString().trimEnd())

        val followup = response.respond {
            content = resultPages.first()
        }
        resultPages.drop(1).forEach { page ->
            followup.createPublicFollowup {
                content = page
            }
        }
    }
}

/** Splits the legacy `/r` multiroll suffix from the dice text so it can be validated independently. */
internal fun parseLegacyMultiroll(rollString: String): Pair<String, Int>?
{
    val suffix = Regex("\\s*x(\\d+)\\s*$", RegexOption.IGNORE_CASE).find(rollString) ?: return Pair(rollString, 1)
    val rollTimes = suffix.groupValues[1].toIntOrNull() ?: return null
    if (rollTimes !in 1..100) return null

    return Pair(rollString.removeRange(suffix.range).trim(), rollTimes)
}
