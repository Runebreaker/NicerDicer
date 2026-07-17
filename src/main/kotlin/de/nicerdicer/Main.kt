package de.nicerdicer

import dev.kord.core.Kord
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object Main
{
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
            println("Interactions were registered!")

            kord.login()
        }
    }
}
