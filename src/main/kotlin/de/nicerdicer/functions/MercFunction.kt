package de.nicerdicer.functions

import de.nicerdicer.db.Database
import de.nicerdicer.util.bold
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.embed

object MercFunction : FunctionBase("merc", "Roll a merc.")
{
    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {
        builder.apply {
            subCommand("roll", "Roll a merc") { }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()

        val merc = Database.getRandomMookClass(MookPool.MERCENARY.toString())

        if (merc == null)
        {
            response.respond {
                content = "Something went wrong while rolling a merc. Please contact a moderator."
            }
            return
        }

        response.respond {
            embed {
                title = merc.name
                description = merc.description
                field {
                    name = "Statline"
                    value = MookPool.MERCENARY.getStatline()
                }
                field {
                    name = "Recommended Skill"
                    value = merc.skill ?: "Any"
                }
                footer {
                    text = "${"Rarity:".bold()} ${merc.pool}"
                }
            }
        }
    }
}