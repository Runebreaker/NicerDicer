package de.nicerdicer.functions

import de.nicerdicer.db.AugmentEntry
import de.nicerdicer.db.Database
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.EmbedBuilder

object AugmentFunction : FunctionBase("augment", "Rolls an augment.")
{
    override fun register(registrar: InteractionRegistrar)
    {
        println("Preparing augments...")
        try {
            Database.init()
            println("Database initialized for augments.")
        } catch (e: Exception) {
            println("Error while preparing augments: ${e.message}")
            e.printStackTrace()
        }

        registrar.command(name, description, ::execute) {
            string("category", "e.g. Breaker or Thinker") {
                required = true
                for (entry in Classification.entries)
                {
                    val caseCorrectedName = entry.name.lowercase().replaceFirstChar { it.uppercase() }
                    choice(caseCorrectedName, caseCorrectedName)
                }
            }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()
        val specifiedCategory = event.interaction.command.strings["category"]!!

        if (!Classification.entries.map { entry -> entry.name }.contains(specifiedCategory.uppercase()))
        {
            response.respond {
                content = "Category $specifiedCategory not found."
            }
            return
        }

        val categoryAsClassification = Classification.valueOf(specifiedCategory.uppercase())

        try {
            val augList = Database.getAugments()
            if (augList.isEmpty()) throw IllegalStateException("No augments present in DB.")
            val rolledAugment = augList.random()
            val pickedAugmentText = pickFromAugment(rolledAugment, categoryAsClassification)
            val cardRegexToRemove = Regex("\\w+\\. ")

            val embedBuilder = EmbedBuilder()
            val footerBuilder = EmbedBuilder.Footer()
            embedBuilder.title = rolledAugment.card.lowercase().replaceFirstChar { it.uppercase() }
            embedBuilder.description = cardRegexToRemove.replaceFirst(pickedAugmentText, "")
            footerBuilder.text = specifiedCategory.lowercase().replaceFirstChar { it.uppercase() }
            embedBuilder.footer = footerBuilder

            response.respond {
                content = "Rolling an augment..."
                embeds = mutableListOf(embedBuilder)
            }
        } catch (e: Exception) {
            println("AugmentFunction.execute error: ${e.message}")
            e.printStackTrace()
            response.respond {
                content = "Failed to roll augment: ${e.message}"
            }
        }
    }

    private fun pickFromAugment(augment: AugmentEntry, classification: Classification): String
    {
        return when (classification)
        {
            Classification.BLASTER -> augment.blaster
            Classification.BREAKER -> augment.breaker
            Classification.BRUTE -> augment.brute
            Classification.CHANGER -> augment.changer
            Classification.MASTER -> augment.master
            Classification.MOVER -> augment.mover
            Classification.SHAKER -> augment.shaker
            Classification.STRANGER -> augment.stranger
            Classification.STRIKER -> augment.striker
            Classification.TINKER -> augment.tinker
            Classification.THINKER -> augment.thinker
            Classification.TRUMP -> augment.trump
        }
    }
}

enum class Classification
{
    BLASTER,
    BREAKER,
    BRUTE,
    CHANGER,
    MASTER,
    MOVER,
    SHAKER,
    STRANGER,
    STRIKER,
    TINKER,
    THINKER,
    TRUMP;
}
