package de.nicerdicer.functions

import dev.kord.core.event.interaction.ActionInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder

abstract class FunctionBase(val functionName: String, val functionDescription: String)
{
    abstract suspend fun defineLayout(builder: ChatInputCreateBuilder)
    abstract suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    open val modalHandlers: Map<String, suspend (ActionInteractionCreateEvent) -> Unit>
        get() = emptyMap()
}