package de.nicerdicer.util

import de.nicerdicer.db.Database
import de.nicerdicer.db.WoundEntry
import dev.kord.common.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Wounds
{
    init
    {
        println("Wounds: will query wounds from DB on demand.")
    }

    fun roll(c: Int = 0, m: Int = 0, l: Int = 0, type: WoundType, location: WoundLocation? = null): List<WoundEffect>
    {
        val woundEffects = mutableListOf<WoundEffect>()
        try
        {
            val allRows = Database.getWounds()
            if (allRows.isEmpty())
            {
                println("Wounds.roll: no wounds in DB.")
                return emptyList()
            }

            data class RowParsed(val name: String, val desc: String, val severity: WoundSeverity, val location: WoundLocation)

            val parsedRows = allRows.mapNotNull { row ->
                val rType = parseWoundType(row.woundType)
                val rSev = parseWoundSeverity(row.woundSeverity)
                val rLoc = parseWoundLocation(row.woundLocation)
                if (rType == null || rSev == null || rLoc == null)
                {
                    println("Wounds.roll: skipping unparsable DB row: $row")
                    null
                } else if (!rType.name.equals(type.name, ignoreCase = true))
                {
                    null
                } else
                {
                    RowParsed(row.woundName, row.woundDescription, rSev, rLoc)
                }
            }

            fun candidatesFor(sev: WoundSeverity, location: WoundLocation): List<RowParsed> =
                parsedRows.filter { it.severity == sev && (it.location == location || it.location == WoundLocation.ANY) }

            repeat(c) {
                val chosenLocation = location ?: WoundLocation.roll()
                val candidate = candidatesFor(WoundSeverity.CRITICAL, chosenLocation)
                if (candidate.isNotEmpty())
                {
                    val chosen = candidate.first()
                    woundEffects.add(WoundEffect(chosen.name, chosen.desc, chosenLocation, WoundSeverity.CRITICAL, type))
                } else
                {
                    println("Wounds.roll: no critical entries for type=$type location=$chosenLocation")
                }
            }

            repeat(m) {
                val chosenLocation = location ?: WoundLocation.roll()
                val candidate = candidatesFor(WoundSeverity.MODERATE, chosenLocation)
                if (candidate.isNotEmpty())
                {
                    val idx = NicerRandom.random.nextInt(0, candidate.size)
                    val chosen = candidate[idx]
                    woundEffects.add(WoundEffect(chosen.name, chosen.desc, chosenLocation, WoundSeverity.MODERATE, type))
                } else
                {
                    println("Wounds.roll: no moderate entries for type=$type location=$chosenLocation")
                }
            }

            repeat(l) {
                val chosenLocation = location ?: WoundLocation.roll()
                val candidate = candidatesFor(WoundSeverity.LESSER, chosenLocation)
                if (candidate.isNotEmpty())
                {
                    val idx = NicerRandom.random.nextInt(0, candidate.size)
                    val chosen = candidate[idx]
                    woundEffects.add(WoundEffect(chosen.name, chosen.desc, chosenLocation, WoundSeverity.LESSER, type))
                } else
                {
                    println("Wounds.roll: no lesser entries for type=$type location=$chosenLocation")
                }
            }
        } catch (e: Exception)
        {
            println("Wounds.roll failed: ${e.message}")
            e.printStackTrace()
        }
        return woundEffects
    }

    fun getDemolished(location: WoundLocation): List<WoundEffect>
    {
        val wounds = Database.getWounds()
            .filter { parseWoundLocation(it.woundLocation) == location && parseWoundType(it.woundType) == WoundType.BASH && parseWoundSeverity(it.woundSeverity) == WoundSeverity.MODERATE }
        return wounds.map { WoundEffect(it.woundName, it.woundDescription, location, WoundSeverity.MODERATE, WoundType.BASH) }
    }

    /**
     * @throws NoSuchElementException if no wound type is found in the database. Should never occur with initialized DB.
     */
    fun rollDevastated(): WoundEffect
    {
        val chosenType = listOf(WoundType.CUT, WoundType.BASH, WoundType.PIERCE, WoundType.REND).random()
        return roll(0, 0, 1, chosenType).first()
    }

    //region Parsers

    private fun parseWoundType(s: String?): WoundType?
    {
        if (s == null) return null
        return WoundType.entries.find {
            it.name.equals(s, ignoreCase = true) || it.name.equals(s.replace(" ", "_"), ignoreCase = true) || it.name.equals(
                s.uppercase(),
                ignoreCase = true
            ) || it.name.equals(s.lowercase().replaceFirstChar { char -> char.uppercase() }, ignoreCase = true)
        }
    }

    private fun parseWoundSeverity(s: String?): WoundSeverity?
    {
        if (s == null) return null
        return WoundSeverity.entries.find {
            it.name.equals(s, ignoreCase = true) || it.name.equals(s.replace(" ", "_"), ignoreCase = true) || it.name.equals(
                s.uppercase(),
                ignoreCase = true
            ) || it.name.equals(s.lowercase().replaceFirstChar { char -> char.uppercase() }, ignoreCase = true)
        }
    }

    private fun parseWoundLocation(s: String?): WoundLocation?
    {
        if (s == null) return null
        return when (s.trim().lowercase())
        {
            "any" -> WoundLocation.ANY
            "head" -> WoundLocation.HEAD
            "torso" -> WoundLocation.TORSO
            "arm", "arms" -> WoundLocation.ARMS
            "leg", "legs" -> WoundLocation.LEGS
            else -> WoundLocation.entries.find { it.name.equals(s, ignoreCase = true) }
        }
    }

    //endregion
}

@Serializable
enum class WoundSeverity
{
    @SerialName("Lesser")
    LESSER,

    @SerialName("Moderate")
    MODERATE,

    @SerialName("Critical")
    CRITICAL;
}

@Serializable
enum class WoundType(val color: Color)
{
    @SerialName("Cut")
    CUT(Color(153, 0, 0)),

    @SerialName("Bash")
    BASH(Color(11, 83, 148)),

    @SerialName("Pierce")
    PIERCE(Color(180, 95, 6)),

    @SerialName("Rend")
    REND(Color(53, 28, 117)),

    @SerialName("Burn")
    BURN(Color(56, 118, 29)),

    @SerialName("Freeze")
    FREEZE(Color(17, 85, 204)),

    @SerialName("Shock")
    SHOCK(Color(191, 144, 0)),

    @SerialName("Poison")
    POISON(Color(182, 215, 168));
}

@Serializable
enum class WoundLocation
{
    @SerialName("Any")
    ANY,

    @SerialName("Head")
    HEAD,

    @SerialName("Torso")
    TORSO,

    @SerialName("Arm")
    ARMS,

    @SerialName("Legs")
    LEGS;

    companion object
    {
        fun roll(): WoundLocation
        {
            return when (NicerRandom.random.nextInt(1, 7))
            {
                1 ->
                {
                    LEGS
                }

                2 ->
                {
                    TORSO
                }

                3 ->
                {
                    TORSO
                }

                4 ->
                {
                    TORSO
                }

                5 ->
                {
                    ARMS
                }

                else ->
                {
                    HEAD
                }
            }
        }
    }
}

data class WoundEffect(val name: String, val description: String, val location: WoundLocation, val severity: WoundSeverity, val type: WoundType)