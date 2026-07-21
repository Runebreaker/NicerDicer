package de.nicerdicer

import dev.kord.core.Kord
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

object Main
{
    val applicationId = "1205518007632134165"
    val token = System.getenv("DISCORD_BOT_TOKEN")
    var kord: Kord? = null
        private set

    @JvmStatic
    fun main(args: Array<String>)
    {
        runBlocking {
            launch { bot() }
        }
    }

    private suspend fun bot()
    {
        if (token == null)
        {
            println("Main: DISCORD_BOT_TOKEN environment variable is not set. Exiting.")
            return
        }

        kord = Kord(token)

        kord?.let { kord ->
            Registry.prepareCommands(kord)

            Registry.startInteractionListeners(kord)

            kord.on<ChatInputCommandInteractionCreateEvent> {
                Registry.handleCommand(this)
            }
            println("Slash command handlers set!")

            kord.login()
        }
    }
}