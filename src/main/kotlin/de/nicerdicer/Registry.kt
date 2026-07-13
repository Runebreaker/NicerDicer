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
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent

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

    val commandMap = mutableMapOf<String, FunctionBase>()

    suspend fun prepareCommands(kord: Kord)
    {
        commands.forEach {
            commandMap[it.name] = it
            it.prepare(kord)
        }
    }

    suspend fun handleCommand(event: ChatInputCommandInteractionCreateEvent)
    {
        with(event)
        {
            val cmd = commandMap[interaction.invokedCommandName]
            cmd?.execute(this)
        }
    }
}
