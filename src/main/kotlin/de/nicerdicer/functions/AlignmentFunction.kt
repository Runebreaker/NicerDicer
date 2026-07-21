package de.nicerdicer.functions

import de.nicerdicer.db.Database
import de.nicerdicer.db.AlignmentEntry
import de.nicerdicer.util.KordUtil
import de.nicerdicer.util.bold
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.createRole
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.user
import kotlinx.coroutines.flow.toList

object AlignmentFunction : FunctionBase("alignment", "Everything to do with alignments.")
{
    val validOrders = listOf("Lawful", "Neutral", "Chaotic")
    val validIntents = listOf("Good", "Neutral", "Evil")
    private val alignmentRoleNames = setOf("Good", "Evil")
    private val alignmentNames = validIntents.flatMap { intent ->
        validOrders.map { order -> alignmentName(order, intent) }
    }

    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {
        builder.apply {
            subCommand("set", "Set your alignment") {
                string("order", "Lawful, Neutral, or Chaotic") {
                    required = true
                    validOrders.forEach {
                        choice(it, it)
                    }
                }
                string("intent", "Good, Neutral, or Evil") {
                    required = true
                    validIntents.forEach {
                        choice(it, it)
                    }
                }
            }
            subCommand("chart", "Show the alignment chart and player counts") { }
            subCommand("show", "Show an alignment, player, or alignment group") {
                string("alignment", "Good, Evil, or a specific alignment") {
                    required = false
                    choice("Good", "Good")
                    choice("Evil", "Evil")
                    alignmentNames.forEach {
                        choice(it, it)
                    }
                }
                user("player", "Show a player's alignment") {
                    required = false
                }
            }
        }
        
        Database.init()
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val guildIdVal = event.interaction.data.guildId.value?.toString()
        if (guildIdVal == null)
        {
            event.interaction.respondPublic { content = "This command can only be used in a guild." }
            return
        }

        val response = event.interaction.deferPublicResponse()
        try
        {
            val subCommand = event.interaction.command.data.options.value?.map { it.name }?.first() ?: "show"
            
            when (subCommand)
            {
                "set" ->
                {
                    val order = event.interaction.command.strings["order"]?.trim().orEmpty()
                    val intent = event.interaction.command.strings["intent"]?.trim().orEmpty()
                    
                    if (order.isBlank() || intent.isBlank())
                    {
                        response.respond { content = "Usage: /alignment set order:<Lawful|Neutral|Chaotic> intent:<Good|Neutral|Evil>" }
                        return
                    }

                    if (order !in validOrders || intent !in validIntents)
                    {
                        response.respond { content = "Invalid order or intent. Order must be Lawful, Neutral, or Chaotic. Intent must be Good, Neutral, or Evil." }
                        return
                    }
                    
                    val userId = event.interaction.user.id.toString()
                    val ok = Database.setAlignment(guildIdVal, userId, order, intent)
                    if (ok)
                    {
                        val displayAlignmentName = alignmentName(order, intent)
                        val roleName = when
                        {
                            intent == "Good" || (order == "Lawful" && intent == "Neutral") -> "Good"
                            intent == "Evil" || (order == "Chaotic" && intent == "Neutral") -> "Evil"
                            else -> null
                        }
                        try
                        {
                            val guild = event.kord.getGuild(Snowflake(guildIdVal))
                            val member = guild.getMember(event.interaction.user.id)
                            val guildRoles = guild.roles.toList()
                            val existingAlignmentRoleIds = guildRoles
                                .filter { role -> alignmentRoleNames.any { it.equals(role.name, ignoreCase = true) } }
                                .map { it.id }
                                .toSet()
                            val memberRoles = (member.roleIds - existingAlignmentRoleIds).toMutableSet()

                            roleName?.let { name ->
                                val alignmentRole = guildRoles.firstOrNull { it.name.equals(name, ignoreCase = true) }
                                    ?: guild.createRole { this.name = name }
                                memberRoles.add(alignmentRole.id)
                            }

                            member.edit {
                                roles = memberRoles
                            }
                        } catch (e: Exception)
                        {
                            println("AlignmentFunction.execute: alignment role update failed for user $userId in guild $guildIdVal: ${e.message}")
                            e.printStackTrace()
                            response.respond {
                                content = "Your alignment has been set to ${displayAlignmentName.bold()}, but the Discord role could not be updated. Check the bot's Manage Roles permission and role hierarchy, then run /alignment set again."
                            }
                            return
                        }

                        response.respond { content = "Your alignment has been set to ${displayAlignmentName.bold()}" }
                    }
                    else
                    {
                        response.respond { content = "Failed to set alignment." }
                    }
                }

                "chart" ->
                {
                    response.respond { content = formatAlignmentChart(Database.getAlignments(guildIdVal)) }
                }
                
                "show" ->
                {
                    val userId = event.interaction.user.id.toString()
                    val target = event.interaction.command.strings["alignment"]
                    val player = event.interaction.command.users["player"]
                    if (target != null && player != null)
                    {
                        response.respond { content = "Choose either an alignment or a player, not both!" }
                        return
                    }

                    if (player != null)
                    {
                        val alignment = Database.getAlignment(guildIdVal, player.id.toString())
                        if (alignment != null)
                        {
                            response.respond {
                                content = "${player.mention}'s alignment is ${alignmentName(alignment.alignmentOrder, alignment.intent).bold()}"
                            }
                        }
                        else
                        {
                            response.respond { content = "${player.mention} has not set an alignment yet." }
                        }
                        return
                    }

                    if (target == null)
                    {
                        val alignment = Database.getAlignment(guildIdVal, userId)
                        if (alignment != null)
                        {
                            response.respond { content = "Your alignment is ${alignmentName(alignment.alignmentOrder, alignment.intent).bold()}" }
                        }
                        else
                        {
                            response.respond { content = "You have not set an alignment yet. Use /alignment set to set your alignment." }
                        }
                        return
                    }

                    val allAlignments = Database.getAlignments(guildIdVal)
                    val matches = filterAlignments(allAlignments, target)

                    if (matches.isEmpty())
                    {
                        response.respond { content = "No players match '$target'." }
                        return
                    }

                    val resultPages = mutableListOf<String>()
                    var resultPage = StringBuilder()
                    for (alignment in matches)
                    {
                        val memberName = KordUtil.getMemberName(event.kord, Snowflake(guildIdVal), Snowflake(alignment.userId))
                        val line = "$memberName: ${alignmentName(alignment.alignmentOrder, alignment.intent)}"
                        if (resultPage.isNotEmpty() && resultPage.length + line.length + 1 > 2_000)
                        {
                            resultPages.add(resultPage.toString())
                            resultPage = StringBuilder()
                        }
                        resultPage.append(line).append("\n")
                    }
                    resultPages.add(resultPage.toString().trimEnd())

                    val followup = response.respond { content = resultPages.first() }
                    resultPages.drop(1).forEach { page ->
                        followup.createPublicFollowup { content = page }
                    }
                }
                
                else ->
                {
                    response.respond { content = "Unknown subcommand. Use set or show." }
                }
            }
        } catch (e: Exception)
        {
            println("AlignmentFunction.execute: unexpected error: ${e.message}")
            e.printStackTrace()
            response.respond { content = "An internal error occurred while handling the alignment command." }
        }
    }

    /** Maps stored alignment axes to the player-facing alignment name. */
    internal fun alignmentName(order: String, intent: String): String =
        if (order == "Neutral" && intent == "Neutral") "True Neutral" else "$order $intent"

    /** Formats current alignment counts as a three-by-three D&D alignment chart for Discord. */
    internal fun formatAlignmentChart(alignments: List<AlignmentEntry>): String
    {
        val alignmentCounts = alignments.groupingBy { alignmentName(it.alignmentOrder, it.intent) }.eachCount()
        val rows = listOf(
            listOf("Lawful" to "Good", "Neutral" to "Good", "Chaotic" to "Good"),
            listOf("Lawful" to "Neutral", "Neutral" to "Neutral", "Chaotic" to "Neutral"),
            listOf("Lawful" to "Evil", "Neutral" to "Evil", "Chaotic" to "Evil"),
        )

        return rows.joinToString(prefix = "```\n", postfix = "\n```", separator = "\n") { row ->
            row.joinToString(" | ") { (order, intent) ->
                val cellAlignmentName = alignmentName(order, intent)
                "$cellAlignmentName: ${alignmentCounts[cellAlignmentName] ?: 0}"
            }
        }
    }

    /** Selects the alignments included by the Good, Evil, or exact-alignment `/alignment show` target. */
    internal fun filterAlignments(alignments: List<AlignmentEntry>, target: String): List<AlignmentEntry> =
        when
        {
            target.equals("good", ignoreCase = true) -> alignments.filter {
                it.intent == "Good" || (it.alignmentOrder == "Lawful" && it.intent == "Neutral")
            }
            target.equals("evil", ignoreCase = true) -> alignments.filter {
                it.intent == "Evil" || (it.alignmentOrder == "Chaotic" && it.intent == "Neutral")
            }
            else -> alignments.filter {
                alignmentName(it.alignmentOrder, it.intent).equals(target, ignoreCase = true)
            }
        }
}
