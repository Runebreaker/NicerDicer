package de.nicerdicer

import de.nicerdicer.functions.AlignmentFunction
import de.nicerdicer.functions.AugmentFunction
import de.nicerdicer.functions.CardFunction
import de.nicerdicer.functions.ChangeDiceFunction
import de.nicerdicer.functions.ConcFunction
import de.nicerdicer.functions.CombatFunction
import de.nicerdicer.functions.FactionFunction
import de.nicerdicer.functions.FlawFunction
import de.nicerdicer.functions.FunctionBase
import de.nicerdicer.functions.InteractionRegistrar
import de.nicerdicer.functions.LegacyRollFunction
import de.nicerdicer.functions.MercFunction
import de.nicerdicer.functions.MookFunction
import de.nicerdicer.functions.PerkFunction
import de.nicerdicer.functions.QuickTagFunction
import de.nicerdicer.functions.ReputationFunction
import de.nicerdicer.functions.RolePermissionsFunction
import de.nicerdicer.functions.RollFunction
import de.nicerdicer.functions.ShutdownFunction
import de.nicerdicer.functions.WoundFunction
import de.nicerdicer.functions.TagFunction
import de.nicerdicer.functions.TerritoryFunction
import dev.kord.core.Kord
import dev.kord.core.event.interaction.GuildComponentInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import kotlinx.coroutines.flow.toList

object Registry
{
    // Every command needs to be registered here to be available in the bot.
    val commands = listOf(
        RollFunction,
        ShutdownFunction,
        CardFunction,
        WoundFunction,
        PerkFunction,
        FlawFunction,
        AugmentFunction,
        CombatFunction,
        LegacyRollFunction,
        ConcFunction,
        ChangeDiceFunction,
        TagFunction,
        QuickTagFunction,
        TerritoryFunction,
        AlignmentFunction,
        RolePermissionsFunction,
        ReputationFunction,
        FactionFunction,
        MookFunction,
        MercFunction
    )

    private val commandHandlers = mutableMapOf<String, suspend (ChatInputCommandInteractionCreateEvent) -> Unit>()

    suspend fun prepareCommands(kord: Kord)
    {
        val registrar = InteractionRegistrar(kord)
        commands.forEach {
            it.register(registrar)
        }

        val commandRegistrations = registrar.commands()
        commandHandlers.clear()
        commandRegistrations.forEach { commandHandlers[it.name] = it.handler }
        synchronizeGlobalCommands(kord, commandRegistrations)

        kord.on<ChatInputCommandInteractionCreateEvent> {
            handleCommand(this)
        }
        kord.on<GuildModalSubmitInteractionCreateEvent> {
            registrar.dispatchModal(this)
        }
        kord.on<GuildComponentInteractionCreateEvent> {
            registrar.dispatchComponent(this)
        }
    }

    suspend fun handleCommand(event: ChatInputCommandInteractionCreateEvent)
    {
        commandHandlers[event.interaction.invokedCommandName]?.invoke(event)
    }

    /** Reconciles Discord's global commands with the currently declared command catalogue. */
    private suspend fun synchronizeGlobalCommands(kord: Kord, commands: List<de.nicerdicer.functions.CommandRegistration>)
    {
        val declaredNames = commands.map { it.name }.toSet()
        val existingCommands = kord.getGlobalApplicationCommands().toList()
        val obsoleteCommandNames = commandNamesToRemove(existingCommands.map { it.name }, declaredNames)
        existingCommands
            .filter { it.name in obsoleteCommandNames }
            .forEach { it.delete() }

        commands.forEach { command ->
            kord.createGlobalChatInputCommand(command.name, command.description, command.configure)
        }
    }
}

/** Identifies Discord commands that the current catalogue no longer declares. */
internal fun commandNamesToRemove(existingNames: Collection<String>, declaredNames: Collection<String>): Set<String> =
    existingNames.filterNot { it in declaredNames }.toSet()
