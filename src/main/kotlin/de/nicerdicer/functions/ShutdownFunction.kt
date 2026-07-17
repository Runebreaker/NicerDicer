package de.nicerdicer.functions

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent

object ShutdownFunction : FunctionBase("shutdown", "Shutdown the bot!") {
    var kord: Kord? = null

    override fun register(registrar: InteractionRegistrar) {
        registrar.command(name, description, ::execute) {
            defaultMemberPermissions = Permissions(Permission.Administrator)
        }
        this.kord = registrar.kord
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent) {
        event.interaction.respondEphemeral {
            content = "Shutting down bot..."
        }
        println("Shutting down bot...")
        kord?.shutdown()
    }

}
