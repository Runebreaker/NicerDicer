package de.nicerdicer.functions

import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent

abstract class FunctionBase(val name: String, val description: String)
{
    /** Declares this feature's interactions so the registry can register them centrally. */
    abstract fun register(registrar: InteractionRegistrar)
    abstract suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
}
