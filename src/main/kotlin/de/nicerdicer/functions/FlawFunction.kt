package de.nicerdicer.functions

import de.nicerdicer.db.Database
import de.nicerdicer.db.PerkEntry
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.EmbedBuilder

object FlawFunction : FunctionBase("flaw", "Rolls flaws.")
{
    // no local caching: fetch from DB in execute()

    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {
        try {
            Database.init()
            println("Database initialized for flaws.")
        } catch (e: Exception) {
            println("Error while preparing flaws: ${e.message}")
            e.printStackTrace()
        }

        builder.apply {
            subCommand("power", "Rolls a power flaw.")
            subCommand("life", "Rolls a power flaw.")
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
                    val list = Database.getPerks("life_flaws")
                    if (list.isEmpty()) throw IllegalStateException("No life flaws available in DB.")
                    list.random()
                }
                else ->
                {
                    chosenType = "Power"
                    val list = Database.getPerks("power_flaws")
                    if (list.isEmpty()) throw IllegalStateException("No power flaws available in DB.")
                    list.random()
                }
            }

            embedBuilder.title = "${rolledPerk.name} (${rolledPerk.card})"
            embedBuilder.description = rolledPerk.text
            footerBuilder.text = rolledPerk.meaning
            embedBuilder.footer = footerBuilder

            response.respond {
                content = "Rolling for a $chosenType flaw..."
                embeds = mutableListOf(embedBuilder)
            }
        } catch (e: Exception) {
            println("FlawFunction.execute error: ${e.message}")
            e.printStackTrace()
            response.respond {
                content = "Failed to roll flaw: ${e.message}"
            }
        }
    }
}