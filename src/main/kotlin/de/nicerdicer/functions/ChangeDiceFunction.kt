package de.nicerdicer.functions

import de.nicerdicer.util.NicerRandom
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder

object ChangeDiceFunction : FunctionBase("throw", "Throw your dice away and get new ones!")
{
    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {

    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()

        val user = event.interaction.user

        NicerRandom.newRandomSeed()

        response.respond {
            content = "${user.mention} threw away their dice and got new ones. Watch out!"
        }
    }
}