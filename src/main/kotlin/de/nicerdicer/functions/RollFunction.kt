package de.nicerdicer.functions

import de.nicerdicer.util.RollResult
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string

object RollFunction : FunctionBase("roll", "Rolls the dice!") {
    override suspend fun prepare(kord: Kord) {
        kord.createGlobalChatInputCommand(name, description) {
            integer(name = "roll_amount", "How many dice to throw; e.g. 3") {
                required = false
            }
            integer(name = "roll_modifier", "Value to add to the result; e.g. 4 or -3") {
                required = false
            }
            integer(name = "roll_times", "How many times to repeat this roll; maximum 100") {
                required = false
            }
            integer("dice_type", "What type of die to use; e.g. d6") {
                required = false
                choice("D2", 2)
                choice("D4", 4)
                choice("D6", 6)
                choice("D8", 8)
                choice("D10", 10)
                choice("D12", 12)
                choice("D20", 20)
            }
            string("note", "e.g. Attack! or DC14 Guts Save") {
                required = false
            }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent) {
        val response = event.interaction.deferPublicResponse()

        val diceType = event.interaction.command.integers["dice_type"]?.toInt() ?: 20
        val diceAmount = event.interaction.command.integers["roll_amount"]?.toInt() ?: 3
        val modifier = event.interaction.command.integers["roll_modifier"]?.toInt() ?: 4
        val rollTimes = event.interaction.command.integers["roll_times"] ?: 1

        if (rollTimes !in 1..100)
        {
            response.respond { content = "Roll times must be between 1 and 100." }
            return
        }

        val resultPages = mutableListOf<String>()
        var resultPage = StringBuilder()
        repeat(rollTimes.toInt())
        {
            val result = RollResult(diceType, diceAmount, modifier)
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

        event.interaction.command.strings["note"]?.let {
            val note = "    /// $it"
            if (resultPage.isNotEmpty() && resultPage.length + note.length > 2_000)
            {
                resultPages.add(resultPage.toString().trimEnd())
                resultPage = StringBuilder()
            }
            resultPage.append(note)
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
