package de.nicerdicer.functions

import de.nicerdicer.db.Database
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.string

object RolePermissionsFunction : FunctionBase("permissions", "Everything for role permissions")
{
    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {
        builder.apply {
            subCommand("mod", "Set the moderator role (admin only)") {
                string("role", "Role mention or ID to mark as moderator") { required = true }
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
            val guild = event.kord.getGuild(Snowflake(guildIdVal))
            val callerId = event.interaction.user.id.toString()
            if (callerId != guild.ownerId.toString())
            {
                response.respond { content = "Only server administrators (guild owner) may use this command." }
                return
            }

            val subCommand = event.interaction.command.data.options.value?.map { it.name }?.first() ?: "mod"
            when (subCommand)
            {
                "mod" ->
                {
                    val roleParam = event.interaction.command.strings["role"]?.trim().orEmpty()
                    if (roleParam.isBlank())
                    {
                        response.respond { content = "Usage: /permissions mod role:<@&role>" }
                        return
                    }

                    val regex = Regex("<@&?(\\d+)>?")
                    val match = regex.find(roleParam)
                    val roleId = match?.groups?.get(1)?.value ?: roleParam.filter { it.isDigit() }
                    if (roleId.isBlank())
                    {
                        response.respond { content = "Could not parse role id from input." }
                        return
                    }

                    val ok = Database.setModRole(guildIdVal, roleId)
                    if (ok)
                    {
                        response.respond { content = "Moderator role set to <@&${roleId}>." }
                    }
                    else
                    {
                        response.respond { content = "Failed to set moderator role." }
                    }
                }

                else -> response.respond { content = "Unknown subcommand. Use mod." }
            }
        } catch (e: Exception)
        {
            println("RolePermissionsFunction.execute: unexpected error: ${e.message}")
            e.printStackTrace()
            response.respond { content = "An internal error occurred while handling the permissions command." }
        }
    }
}