package de.nicerdicer.functions

import de.nicerdicer.db.Database
import de.nicerdicer.db.MookClassEntry
import de.nicerdicer.util.NicerRandom
import de.nicerdicer.util.bold
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.embed

object MookFunction : FunctionBase("mook", "Roll a mook.")
{
    override fun register(registrar: InteractionRegistrar)
    {
        registrar.command(name, description, ::execute) {
            subCommand("roll", "Roll a mook") { }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()

        val mookClasses = mutableListOf<MookClassEntry?>()
        val hasTwoClasses = NicerRandom.nextRoll(100) <= 20

        mookClasses.add(rollRandomMookClass())
        if (hasTwoClasses) mookClasses.add(rollRandomMookClass())

        if (mookClasses.contains(null))
        {
            response.respond {
                content = "Something went wrong while rolling a mook. Please contact a moderator."
            }
            return
        }

        val result = mookClasses.first()!!
        val hasRare = mookClasses.map { MookPool.fromString(it!!.pool) }.contains(MookPool.RARE)

        val followupResponse = response.respond {
            embed {
                title = result.name
                description = result.description
                field {
                    name = "Statline"
                    value = if (hasRare) MookPool.RARE.getStatline() else MookPool.GENERAL.getStatline()
                }
                field {
                    name = "Recommended Skill"
                    value = result.skill ?: "Any"
                }
                footer {
                    text = "${"Rarity:".bold()} ${result.pool}"
                }
            }
        }

        if (!hasTwoClasses) return

        val secondResult = mookClasses.last()!!

        followupResponse.createPublicFollowup {
            content = "Your mook rolled a second class!"
            embed {
                title = secondResult.name
                description = secondResult.description
                field {
                    name = "Recommended Skill"
                    value = secondResult.skill ?: "Any"
                }
                footer {
                    text = "${"Rarity:".bold()} ${secondResult.pool}"
                }
            }
        }
    }
}

private fun rollRandomMookClass(): MookClassEntry?
{
    val isRare = NicerRandom.nextRoll(100) <= 20
    return Database.getRandomMookClass(if (isRare) MookPool.RARE.toString() else MookPool.GENERAL.toString())
}

enum class MookPool {
    GENERAL,
    RARE,
    MERCENARY;

    override fun toString(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    /**
     * Does not throw. Returns statline for General per default
     */
    fun getStatline(): String = when (this) {
        RARE -> "5, 4, 4, 3, 3, 2, 2"
        MERCENARY -> "5, 5, 4, 4, 4, 3, 2"
        else -> "4, 4, 3, 3, 3, 2, 2"
    }

    companion object {
        /**
         * Does not throw. Returns General per default
         */
        fun fromString(value: String): MookPool = when (value.lowercase()) {
            "rare" -> RARE
            "mercenary" -> MERCENARY
            else -> GENERAL
        }
    }
}
