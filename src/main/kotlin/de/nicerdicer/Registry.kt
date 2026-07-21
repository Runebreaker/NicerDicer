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
import dev.kord.core.event.interaction.ActionInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.GlobalApplicationCommandCreateBuilder
import dev.kord.rest.builder.interaction.GlobalChatInputCreateBuilder
import dev.kord.rest.builder.interaction.MultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.OptionsBuilder
import dev.kord.rest.builder.interaction.SubCommandBuilder
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.security.MessageDigest

object Registry
{
    private val cacheFile = File(".command_cache.hash")

    // Every command needs to be registered here to be available in the bot.
    val registeredCommands = listOf(
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

    val allInteractionHandlers: Map<String, suspend (ActionInteractionCreateEvent) -> Unit> by lazy {
        registeredCommands.flatMap { it.modalHandlers.entries }.associate { it.toPair() }
    }

    fun startInteractionListeners(kord: Kord)
    {
        kord.on<ActionInteractionCreateEvent> {
            val handler = allInteractionHandlers.entries.firstOrNull { (prefix, _) ->
                interaction.data.data.customId.value?.startsWith(prefix) ?: false
            }?.value

            if (handler != null)
            {
                handler(this)
            }
        }
        println("Interaction Listeners were registered!")
    }

    suspend fun prepareCommands(kord: Kord)
    {
        val signatureList = mutableListOf<String>()
        var cachedHash = if (cacheFile.exists()) cacheFile.readText().trim() else ""

        kord.createGlobalApplicationCommands {
            registeredCommands.forEach { function ->
                println("Preparing command: ${function.functionName}")
                input(function.functionName, function.functionDescription) {
                    function.defineLayout(this)
                    options?.let { options ->
                        val sb = StringBuilder()
                        for (opt in options)
                        {
                            sb.append(opt.name).append(opt.description)
                                .append(opt.default).append(opt.autocomplete)
                                .append(opt.required).append(opt.type)

                            if (opt is SubCommandBuilder)
                            for (subOpt in opt.options ?: emptyList())
                            {
                                sb.append(subOpt.name).append(subOpt.description)
                                    .append(subOpt.default).append(subOpt.autocomplete)
                                    .append(subOpt.required).append(subOpt.type)
                            }
                        }
                        signatureList.add(sb.toString())
                    }
                }
                commandMap[function.functionName] = function
            }

            val commandsHash = signatureList.joinToString(":").toMd5()
            if (commandsHash == cachedHash)
            {
                println("Command structure identical. Skipping registration...")
                return
            }

            println("Deleting old commands...")
            kord.getGlobalApplicationCommands().collect {
                it.delete()
            }

            cachedHash = commandsHash
            println("Change in command structure detected. Registering commands with discord...")
        }.catch {
            it.printStackTrace()
        }.onEach {
            println("Registering ${it.name}...")
        }.onCompletion {
            cacheFile.writeText(cachedHash)
        }.collect()
    }

    suspend fun handleCommand(event: ChatInputCommandInteractionCreateEvent)
    {
        with(event)
        {
            val cmd = commandMap[interaction.invokedCommandName]
            cmd?.execute(this)
        }
    }

    private fun String.toMd5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
