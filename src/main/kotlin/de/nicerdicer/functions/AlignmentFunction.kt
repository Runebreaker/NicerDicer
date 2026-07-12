package de.nicerdicer.functions

import de.nicerdicer.db.Database
import de.nicerdicer.util.bold
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.createRole
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.string
import kotlinx.coroutines.flow.toList

object AlignmentFunction : FunctionBase("alignment", "Everything to do with alignments.")
{
    val validOrders = listOf("Lawful", "Neutral", "Chaotic")
    val validIntents = listOf("Good", "Neutral", "Evil")
    private val alignmentRoleNames = setOf("Good", "Evil")

    override suspend fun prepare(kord: Kord)
    {
        kord.createGlobalChatInputCommand(name, description) {
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
            subCommand("show", "Show your alignment") { }
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
                        val alignmentName = alignmentRoleName(order, intent)
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
                                content = "Your alignment has been set to ${alignmentName.bold()}, but the Discord role could not be updated. Check the bot's Manage Roles permission and role hierarchy, then run /alignment set again."
                            }
                            return
                        }

                        response.respond { content = "Your alignment has been set to ${alignmentName.bold()}" }
                    }
                    else
                    {
                        response.respond { content = "Failed to set alignment." }
                    }
                }
                
                "show" ->
                {
                    val userId = event.interaction.user.id.toString()
                    val alignment = Database.getAlignment(guildIdVal, userId)
                    if (alignment != null)
                    {
                        val alignmentStr = "${alignment.alignmentOrder} ${alignment.intent}"
                        response.respond { content = "Your alignment is ${"$alignmentStr".bold()}" }
                    }
                    else
                    {
                        response.respond { content = "You have not set an alignment yet. Use /alignment set to set your alignment." }
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

    /** Maps stored alignment axes to the Discord role name used to identify a player's alignment. */
    internal fun alignmentRoleName(order: String, intent: String): String =
        if (order == "Neutral" && intent == "Neutral") "True Neutral" else "$order $intent"
}
