package de.nicerdicer.functions

import de.nicerdicer.util.InteractableWoundsUtil
import de.nicerdicer.util.WoundEffect
import de.nicerdicer.util.WoundLocation
import de.nicerdicer.util.WoundType
import de.nicerdicer.util.Wounds
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.response.DeferredPublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.behavior.interaction.updatePublicMessage
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.event.interaction.ActionInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildButtonInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Event
import dev.kord.rest.builder.component.ActionRowBuilder
import dev.kord.rest.builder.component.ButtonBuilder
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.x.emoji.Emojis
import kotlin.collections.get

object WoundFunction : FunctionBase("wounds", "Everything concerning Wounds.")
{
    // Has to be 5, since in the worst case, 5 action row buttons are needed
    const val EMBED_BATCH_SIZE = 5

    val severityPattern = Regex("\\d+[cml]")
    var wounds = Wounds()

    override val modalHandlers: Map<String, suspend (ActionInteractionCreateEvent) -> Unit> = mapOf(
        "wounds_button_" to { handleWoundsButton(it as GuildButtonInteractionCreateEvent) }
    )

    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {
        builder.apply {
            string("amount", "e.g. 1m1l") {
                required = true
            }
            string("type", "e.g. bash") {
                required = true
                for (type in WoundType.entries)
                {
                    choice(type.name.lowercase().replaceFirstChar { it.uppercase() }, type.name)
                }
            }
            string("location", "e.g. head, default is random") {
                required = false
                for (location in WoundLocation.entries)
                {
                    choice(location.name.lowercase().replaceFirstChar { it.uppercase() }, location.name)
                }
            }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()
        val amountString = event.interaction.command.strings["amount"]!!
        var c = 0
        var m = 0
        var l = 0

        severityPattern.findAll(amountString).forEach { matchResult ->
            val woundAmount = matchResult.value.dropLast(1).toInt()
            when (matchResult.value.last())
            {
                'c' -> c += woundAmount
                'm' -> m += woundAmount
                'l' -> l += woundAmount
            }
        }

        if (c == 0 && m == 0 && l == 0)
        {
            response.respond {
                content = "You have to specify, how many wounds to roll! e.g. 2m1l"
            }
            return
        }

        val typeString = event.interaction.command.strings["type"]!!.uppercase()
        if (!WoundType.entries.map { wound -> wound.name }.contains(typeString))
        {
            response.respond {
                content = "Wound type $typeString not found."
            }
            return
        }
        val type = WoundType.valueOf(typeString)

        val locationString = event.interaction.command.strings["location"]?.uppercase()
        val location = locationString?.let {
            if (WoundLocation.entries.map { location -> location.name }.contains(locationString) && WoundLocation.valueOf(it) != WoundLocation.ANY) WoundLocation.valueOf(it)
            else null
        }

        val rolledWounds = wounds.roll(c, m, l, type, location)

        val embedBatches = makeWoundEmbedBatches(rolledWounds, EMBED_BATCH_SIZE)

        respondWithEmbedBatches(response, embedBatches)
    }

    private fun makeWoundEmbedBatches(wounds: List<WoundEffect>, batchSize: Int, appendix: String? = null): MutableList<Pair<MutableList<EmbedBuilder>, ActionRowBuilder?>>
    {
        val woundBatches = mutableListOf<MutableList<WoundEffect>>()
        val mutableWoundList = wounds.toMutableList()

        while (mutableWoundList.isNotEmpty())
        {
            val batch = mutableListOf<WoundEffect>()
            while (mutableWoundList.isNotEmpty() && batch.size < batchSize) batch.add(mutableWoundList.removeFirst())
            woundBatches.add(batch)
        }

        val embedBatches = mutableListOf<Pair<MutableList<EmbedBuilder>, ActionRowBuilder?>>()
        for (woundBatch in woundBatches)
        {
            val embedBatch = mutableListOf<EmbedBuilder>()
            for (wound in woundBatch)
            {
                val newEmbed = EmbedBuilder()
                val footer = EmbedBuilder.Footer()
                footer.text = "Location: ${wound.location.toString().lowercase().replaceFirstChar { it.uppercase() }} - Severity: ${
                    wound.severity.toString().lowercase().replaceFirstChar { it.uppercase() }
                }${appendix?.let { " - $it" } ?: ""}"

                newEmbed.title = wound.name
                newEmbed.description = wound.description
                newEmbed.color = wound.type.color
                newEmbed.footer = footer

                embedBatch.add(newEmbed)
            }
            embedBatches.add(Pair(embedBatch, createWoundsActionRow(woundBatch)))
        }

        return embedBatches
    }

    private suspend fun respondWithEmbedBatches(response: DeferredPublicMessageInteractionResponseBehavior, embedBatches: MutableList<Pair<MutableList<EmbedBuilder>, ActionRowBuilder?>>)
    {
        if (embedBatches.isEmpty())
        {
            response.respond {
                content = "No wounds to display!"
            }
            return
        }

        val firstBatch = embedBatches.removeFirst()
        val returnedResponse = response.respond {
            embeds = firstBatch.first
            components = firstBatch.second?.let { mutableListOf(it) }
        }
        while (embedBatches.isNotEmpty())
        {
            val nextBatch = embedBatches.removeFirst()
            returnedResponse.createPublicFollowup {
                embeds = nextBatch.first
                components = nextBatch.second?.let { mutableListOf(it) }
            }
        }
    }

    private fun createWoundsActionRow(woundEffects: List<WoundEffect>): ActionRowBuilder?
    {
        val actionRow = ActionRowBuilder().apply {
            var buttonNumber = 0
            for (woundEffect in woundEffects)
            {
                if (woundEffect.name in InteractableWoundsUtil.interactableWounds.map { it.key })
                {
                    interactionButton(ButtonStyle.Primary, "wounds_button_${buttonNumber++}_${woundEffect.location}") {
                        label = woundEffect.name
                        emoji = DiscordPartialEmoji(name = ReactionEmoji.Unicode(if (woundEffect.name == "Demolished") Emojis.mag.unicode else Emojis.gameDie.unicode).name)
                    }
                }
            }
        }

        if (actionRow.components.isEmpty()) return null
        return actionRow
    }

    private suspend fun handleWoundsButton(event: GuildButtonInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()

        val woundLocation = event.interaction.component.customId?.removePrefix("wounds_button_")?.split("_")?.lastOrNull()?.let {
            try
            {
                WoundLocation.valueOf(it.uppercase())
            } catch (e: Exception)
            {
                println("WoundFunction.prepare: Error occurred while parsing wound location from button ${event.interaction.component.customId}: ${e.message}")
                WoundLocation.ANY
            }
        } ?: WoundLocation.ANY
        val woundName = event.interaction.component.label
        val rolledEffects = InteractableWoundsUtil.interactableWounds[woundName]?.invoke(woundLocation) ?: emptyList()

        val buttons = event.interaction.message.actionRows.first().interactionButtons
        val buttonBuildersAdjusted = buttons.map { button ->
            ButtonBuilder.InteractionButtonBuilder(ButtonStyle.Primary, button.key).apply {
                label = button.value.label
                emoji = DiscordPartialEmoji(name = button.value.emoji?.name)
                disabled = button.value.customId == event.interaction.component.customId || button.value.disabled
            }
        }

        event.interaction.message.edit {
            components = mutableListOf(ActionRowBuilder().apply { components.addAll(buttonBuildersAdjusted) } )
        }

        val embedBatches = makeWoundEmbedBatches(rolledEffects, EMBED_BATCH_SIZE, "From $woundName")

        respondWithEmbedBatches(response, embedBatches)
    }
}