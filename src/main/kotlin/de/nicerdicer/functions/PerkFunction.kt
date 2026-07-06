package de.nicerdicer.functions

import de.nicerdicer.db.Database
import de.nicerdicer.db.PerkEntry
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.EmbedBuilder

object PerkFunction : FunctionBase("perk", "Rolls perks.")
{
    override suspend fun prepare(kord: Kord)
    {
        println("Preparing perks...")
        try {
            Database.init()
            println("Database initialized for perks.")
        } catch (e: Exception) {
            println("Error while preparing perks: ${e.message}")
            e.printStackTrace()
        }

        kord.createGlobalChatInputCommand(name, description)
        {
            subCommand("power", "Rolls a power perk.")
            subCommand("life", "Rolls a power perk.")
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()

        val embedBuilder = EmbedBuilder()
        val footerBuilder = EmbedBuilder.Footer()
        val perkType = event.interaction.command.data.options.value?.map { it.name }?.first()
        var chosenType: String?

        try {
            val rolledPerk: PerkEntry = when (perkType)
            {
                "life" ->
                {
                    chosenType = "Life"
                    val list = Database.getPerks("life_perks")
                    if (list.isEmpty()) throw IllegalStateException("No life perks available in DB.")
                    list.random()
                }
                else ->
                {
                    chosenType = "Power"
                    val list = Database.getPerks("power_perks")
                    if (list.isEmpty()) throw IllegalStateException("No power perks available in DB.")
                    list.random()
                }
            }

            embedBuilder.title = "${rolledPerk.name} (${rolledPerk.card})"
            embedBuilder.description = rolledPerk.text
            footerBuilder.text = rolledPerk.meaning
            embedBuilder.footer = footerBuilder

            response.respond {
                content = "Rolling for a $chosenType perk..."
                embeds = mutableListOf(embedBuilder)
            }
        } catch (e: Exception) {
            println("PerkFunction.execute error: ${e.message}")
            e.printStackTrace()
            response.respond {
                content = "Failed to roll perk: ${e.message}"
            }
        }
    }
}