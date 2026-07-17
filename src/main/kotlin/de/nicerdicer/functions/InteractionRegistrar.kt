package de.nicerdicer.functions

import dev.kord.core.Kord
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildComponentInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder

internal data class CommandRegistration(
    val name: String,
    val description: String,
    val configure: GlobalChatInputCreateBuilder.() -> Unit,
    val handler: suspend (ChatInputCommandInteractionCreateEvent) -> Unit,
)

/** Collects feature interaction declarations so the registry can install them through one central path. */
class InteractionRegistrar internal constructor(internal val kord: Kord)
{
    private val commandRegistrations = mutableListOf<CommandRegistration>()
    private val modalHandlers = mutableListOf<suspend (GuildModalSubmitInteractionCreateEvent) -> Unit>()
    private val componentHandlers = mutableListOf<suspend (GuildComponentInteractionCreateEvent) -> Unit>()

    /** Records one slash-command schema and handler for centralized registration and dispatch. */
    fun command(
        name: String,
        description: String,
        handler: suspend (ChatInputCommandInteractionCreateEvent) -> Unit,
        configure: GlobalChatInputCreateBuilder.() -> Unit = {},
    )
    {
        require(commandRegistrations.none { it.name == name }) { "Command '$name' is registered more than once." }
        commandRegistrations.add(CommandRegistration(name, description, configure, handler))
    }

    /** Adds a modal handler to the central dispatcher for feature-owned modal processing. */
    fun modal(handler: suspend (GuildModalSubmitInteractionCreateEvent) -> Unit)
    {
        modalHandlers.add(handler)
    }

    /** Adds a component handler to the central dispatcher for future buttons and select menus. */
    fun component(handler: suspend (GuildComponentInteractionCreateEvent) -> Unit)
    {
        componentHandlers.add(handler)
    }

    /** Supplies the command declarations to the registry's Discord synchronization step. */
    internal fun commands(): List<CommandRegistration> = commandRegistrations.toList()

    /** Sends a modal submission to the feature handlers registered for modal processing. */
    internal suspend fun dispatchModal(event: GuildModalSubmitInteractionCreateEvent)
    {
        modalHandlers.forEach { it(event) }
    }

    /** Sends a component interaction to future button and select handlers. */
    internal suspend fun dispatchComponent(event: GuildComponentInteractionCreateEvent)
    {
        componentHandlers.forEach { it(event) }
    }
}
