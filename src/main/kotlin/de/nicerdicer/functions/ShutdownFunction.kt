package de.nicerdicer.functions

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder

object ShutdownFunction : FunctionBase("shutdown", "Shutdown the bot!") {
    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {
        builder.apply {
            defaultMemberPermissions = Permissions(Permission.Administrator)
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent) {
        event.interaction.respondEphemeral {
            content = "Shutting down bot..."
        }
        println("Shutting down bot...")
        event.kord.shutdown()
    }

}