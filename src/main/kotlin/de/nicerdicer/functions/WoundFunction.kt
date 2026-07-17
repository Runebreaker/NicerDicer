package de.nicerdicer.functions

import de.nicerdicer.util.WoundEffect
import de.nicerdicer.util.WoundLocation
import de.nicerdicer.util.WoundType
import de.nicerdicer.util.Wounds
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.EmbedBuilder

object WoundFunction : FunctionBase("wounds", "Everything concerning Wounds.")
{
    val severityPattern = Regex("\\d+[cml]")
    var wounds = Wounds()

    override fun register(registrar: InteractionRegistrar)
    {
        registrar.command(name, description, ::execute) {
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
        var invalidLocation = false
        val location = locationString?.let {
            if (WoundLocation.entries.map { location -> location.name }.contains(locationString) && WoundLocation.valueOf(it) != WoundLocation.ANY) WoundLocation.valueOf(it)
            else
            {
                invalidLocation = true
                null
            }
        }

        val rolledWounds = wounds.roll(c, m, l, type, location)
        val demolishedWounds = mutableListOf<WoundEffect>()
        val embedBuilders = mutableListOf<EmbedBuilder>()

        for (wound in rolledWounds.toList())
        {
            when (wound.name.lowercase())
            {
                "demolished" -> demolishedWounds.addAll(wounds.getDemolished(wound.location))
            }
        }

        for (wound in rolledWounds)
        {
            val newEmbed = EmbedBuilder()
            val footer = EmbedBuilder.Footer()
            footer.text = "Location: ${wound.location.toString().lowercase().replaceFirstChar { it.uppercase() }} - Severity: ${
                wound.severity.toString().lowercase().replaceFirstChar { it.uppercase() }
            }"

            newEmbed.title = wound.name
            newEmbed.description = wound.description
            newEmbed.color = type.color
            newEmbed.footer = footer

            embedBuilders.add(newEmbed)
        }

        for (wound in demolishedWounds)
        {
            val newEmbed = EmbedBuilder()
            val footer = EmbedBuilder.Footer()
            footer.text = "Location: ${wound.location.toString().lowercase().replaceFirstChar { it.uppercase() }} - Severity: ${
                wound.severity.toString().lowercase().replaceFirstChar { it.uppercase() }
            } - From Demolished"

            newEmbed.title = wound.name
            newEmbed.description = wound.description
            newEmbed.color = type.color
            newEmbed.footer = footer
            embedBuilders.add(newEmbed)
        }

        val embedBatches = mutableListOf<MutableList<EmbedBuilder>>()
        while (embedBuilders.isNotEmpty())
        {
            val batch = mutableListOf<EmbedBuilder>()
            while (embedBuilders.isNotEmpty() && batch.size < 10) batch.add(embedBuilders.removeFirst())
            embedBatches.add(batch)
        }

        val returnedResponse = response.respond {
            content =
                "${if (invalidLocation) "Location was invalid! " else ""}Rolling for ${if (c > 0) "${c}c" else ""}${if (m > 0) "${m}m" else ""}${if (l > 0) "${l}l" else ""} ${
                    type.toString().lowercase().replaceFirstChar { it.uppercase() }
                } to ${location?.toString()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Anywhere"}!"
            embeds = if (embedBatches.isNotEmpty()) embedBatches.removeFirst() else null
        }
        while (embedBatches.isNotEmpty())
        {
            returnedResponse.createPublicFollowup {
                embeds = embedBatches.removeFirst()
            }
        }
    }
}
