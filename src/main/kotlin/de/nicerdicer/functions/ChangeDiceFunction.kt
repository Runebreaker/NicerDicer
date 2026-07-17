package de.nicerdicer.functions

import de.nicerdicer.util.NicerRandom
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent

object ChangeDiceFunction : FunctionBase("throw", "Throw your dice away and get new ones!")
{
    override fun register(registrar: InteractionRegistrar)
    {
        registrar.command(name, description, ::execute)
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
