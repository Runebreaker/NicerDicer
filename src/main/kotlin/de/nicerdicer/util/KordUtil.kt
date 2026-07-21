package de.nicerdicer.util

import de.nicerdicer.db.Database
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.interaction.InteractionCreateEvent

object KordUtil {
    /**
     * Returns the guild member's effective name when possible, otherwise falls back to user.effectiveName.
     * Logs errors verbosely and never throws.
     */
    suspend fun getMemberName(kord: Kord?, guildId: Snowflake?, user: User): String {
        guildId?.let {
            try {
                val guild = kord?.getGuild(it)
                val member = guild?.getMember(user.id)
                member?.effectiveName?.let { memberName ->
                    return memberName
                }
            } catch (e: Exception) {
                println("KordUtil.getMemberName: failed to fetch member name for user=${user.id} guild=$guildId: ${e.message}")
                e.printStackTrace()
            }
        }
        return user.effectiveName
    }

    /**
     * Returns the guild member's effective name when possible, otherwise falls back to user.effectiveName.
     * Logs errors verbosely and never throws.
     */
    suspend fun getMemberName(kord: Kord?, guildId: Snowflake?, userId: Snowflake): String {
        guildId?.let {
            try {
                val guild = kord?.getGuild(it)
                val member = guild?.getMember(userId)
                member?.effectiveName?.let { memberName ->
                    return memberName
                }
            } catch (e: Exception) {
                println("KordUtil.getMemberName: failed to fetch member name for user=$userId guild=$guildId: ${e.message}")
                e.printStackTrace()
            }
        }
        return "Unknown User"
    }

    /**
     * Check whether the given user is a moderator for the guild (has the configured mod role).
     */
    suspend fun isModerator(kord: Kord?, guildId: String, user: User): Boolean {
        val modRole = Database.getModRole(guildId) ?: return false
        val g = kord?.getGuild(Snowflake(guildId)) ?: return false
        val member = g.getMember(user.id)
        return member.roleIds.contains(Snowflake(modRole))
    }

    /**
     * Check whether the given event has been triggered by a moderator (has the configured mod role).
     */
    suspend fun isModerator(event: InteractionCreateEvent): Boolean {
        val kord = event.kord
        val guildId = event.interaction.data.guildId.value?.toString()
        val user = event.interaction.user

        return guildId?.let { isModerator(kord, it, user) } ?: false
    }

    /**
     * Trys to get the set mod role for the given guild. Returns null if no mod role is set or if the role cannot be fetched.
     */
    suspend fun getModRoleOrNull(guild: Guild): Role? {
        val modRoleId = Database.getModRole(guild.id.value.toString()) ?: return null

        try
        {
            return guild.getRole(Snowflake(modRoleId))
        } catch (e: Exception) {
            println("KordUtil.getModRole: failed to fetch mod role for guild=${guild.id.value} modRoleId=$modRoleId: ${e.message}")
            e.printStackTrace()
        }

        return null
    }
}
