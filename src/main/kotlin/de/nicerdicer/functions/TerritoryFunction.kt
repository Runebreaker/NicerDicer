package de.nicerdicer.functions

import de.nicerdicer.db.Database
import de.nicerdicer.util.KordUtil.getMemberName
import de.nicerdicer.util.bold
import de.nicerdicer.util.box
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.addFile
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import java.util.ArrayDeque
import java.awt.Color

object TerritoryFunction : FunctionBase("territory", "Everything concerning territories.")
{
    var kord: Kord? = null

    // Map of territory id -> starting pixel coordinate (x,y) inside the region.
    private val TERRITORY_COORDS: Map<Int, Pair<Int, Int>> = mapOf(
        1 to (200 to 150),
        2 to (300 to 300),
        3 to (450 to 450),
        4 to (400 to 600),
        5 to (250 to 900),
        6 to (500 to 900),
        7 to (700 to 1000),
        8 to (900 to 950),
        9 to (950 to 500),
        10 to (1000 to 300),
        11 to (1000 to 100),
        12 to (800 to 100),
        13 to (600 to 200),
        14 to (800 to 300),
        15 to (700 to 400),
        16 to (850 to 500),
        17 to (600 to 600),
        18 to (600 to 750),
        19 to (750 to 850),
        20 to (1000 to 750)
    )

    // Predefined colors
    private const val COLOR_UNCLAIMED = "#FFFFFF"  // White
    private const val COLOR_GOOD = "#FFFF00"       // Yellow
    private const val COLOR_EVIL = "#800080"       // Purple
    private const val COLOR_QUEST = "#40E0D0"      // Turquoise
    private const val COLOR_CHALLENGED = "#212121" // Dark Gray

    override fun register(registrar: InteractionRegistrar)
    {
        this.kord = registrar.kord
        // register command with subcommands: claim, release, list, map, challenge, rename
        registrar.command(name, description, ::execute) {
            subCommand("claim", "Claim a territory (type inferred from your alignment)") {
                string("id", "Territory id (number)") { required = true }
                string("name", "Optional: give this territory a custom name") { required = false }
            }
            subCommand("fclaim", "Claim a territory for your faction (type inferred from faction alignment)") {
                string("id", "Territory id") { required = true }
                string("name", "Optional: give this territory a custom name") {}
            }
            subCommand("release", "Release a territory you own (mods can release any)") {
                string("id", "Territory id (number)") { required = true }
            }
            subCommand("quest", "Claim a territory for a Quest (mods only)") {
                string("id", "Territory id (number)") { required = true }
                string("name", "Optional: give this territory a custom name") { required = false }
            }
            subCommand("challenge", "Mark a territory as challenged") {
                string("id", "Territory id (number)") { required = true }
            }
            subCommand("rename", "Rename a territory you own") {
                string("id", "Territory id (number)") { required = true }
                string("name", "New territory name") { required = true }
            }
            subCommand("update", "Update territory colors based on alignment") { }
            subCommand("list", "List all territories and owners") { }
            subCommand("map", "Show current map image with colored territories") { }
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

        try
        {
            Database.initializeTerritories(TERRITORY_COORDS.keys, guildIdVal)
        } catch (e: Exception)
        {
            println("TerritoryFunction.execute: initializeTerritories for guild $guildIdVal failed: ${e.message}")
            e.printStackTrace()
        }

        val response = event.interaction.deferPublicResponse()
        try
        {
            val subCommand = event.interaction.command.data.options.value?.map { it.name }?.first() ?: "list"

            when (subCommand)
            {
                "claim" -> {
                    val idText = event.interaction.command.strings["id"]?.trim().orEmpty()
                    val nameOpt = event.interaction.command.strings["name"]?.trim()
                    val id = idText.toIntOrNull()

                    if (id == null)
                    {
                        response.respond { content = "Usage: /territory claim id:<number> [name:optional]" }
                        return
                    }

                    if (!TERRITORY_COORDS.containsKey(id))
                    {
                        response.respond { content = "Territory $id is not claimable. Only territories listed in '/territory list' may be claimed." }
                        return
                    }

                    val ownerId = event.interaction.user.id.toString()

                    // Get user's alignment to determine territory type
                    val alignment = Database.getAlignment(guildIdVal, ownerId)
                    if (alignment == null)
                    {
                        response.respond { content = "You must set your alignment first. Use /alignment set to set your alignment." }
                        return
                    }

                    // Determine type based on alignment
                    val type = when
                    {
                        alignment.intent == "Good" -> "Good"
                        alignment.intent == "Evil" -> "Evil"
                        alignment.intent == "Neutral" && alignment.alignmentOrder == "Lawful" -> "Good"
                        alignment.intent == "Neutral" && alignment.alignmentOrder == "Chaotic" -> "Evil"
                        alignment.intent == "Neutral" && alignment.alignmentOrder == "Neutral" ->
                        {
                            response.respond { content = "True Neutral cannot claim territories." }
                            return
                        }

                        else ->
                        {
                            response.respond { content = "Could not determine territory type from alignment." }
                            return
                        }
                    }

                    val ok = Database.claimTerritory(id, ownerId, guildIdVal, nameOpt, type, "user")
                    if (!ok)
                    {
                        response.respond { content = "Failed to claim territory $id. It may already be owned by someone else." }
                        return
                    }

                    try
                    {
                        val outPath = renderFullMapWithClaims(guildIdVal)
                        val f = File(outPath)
                        response.respond {
                            content = "Territory $id claimed as $type (from your ${alignment.alignmentOrder} ${alignment.intent} alignment) and map updated."
                            addFile(f.toPath())
                        }
                    } catch (e: Exception)
                    {
                        println("TerritoryFunction.execute: no base map found or failed generating map after claim for id $id: ${e.message}")
                        e.printStackTrace()
                        response.respond { content = "Territory claimed, but something went wrong with map generation." }
                    }
                }

                "fclaim" -> {
                    val idText = event.interaction.command.strings["id"]?.trim().orEmpty()
                    val nameOpt = event.interaction.command.strings["name"]?.trim()
                    val id = idText.toIntOrNull()

                    if (id == null)
                    {
                        response.respond { content = "Usage: /territory fclaim id:<number> [name:optional]" }
                        return
                    }

                    if (!TERRITORY_COORDS.containsKey(id))
                    {
                        response.respond { content = "Territory $id is not claimable. Only territories listed in '/territory list' may be claimed." }
                        return
                    }

                    val userId = event.interaction.user.id.toString()

                    val faction = Database.getFactionOfUser(guildIdVal, userId)

                    if (faction == null)
                    {
                        response.respond { content = "You are not in a faction." }
                        return
                    }

                    val factionName = faction.name

                    val ok = Database.claimTerritory(id, factionName, guildIdVal, nameOpt, faction.color, "faction")
                    if (!ok)
                    {
                        response.respond { content = "Failed to claim territory $id. It may already be owned by someone else." }
                        return
                    }

                    try
                    {
                        val outPath = renderFullMapWithClaims(guildIdVal)
                        val f = File(outPath)
                        response.respond {
                            content = "Territory $id claimed for faction ${faction.name} and map updated."
                            addFile(f.toPath())
                        }
                    } catch (e: Exception)
                    {
                        println("TerritoryFunction.execute: no base map found or failed generating map after claim for id $id: ${e.message}")
                        e.printStackTrace()
                        response.respond { content = "Territory claimed, but something went wrong with map generation." }
                    }
                }

                "release" -> {
                    val idText = event.interaction.command.strings["id"]?.trim().orEmpty()
                    val id = idText.toIntOrNull()
                    if (id == null)
                    {
                        response.respond { content = "Usage: /territory release id:<number>" }
                        return
                    }

                    if (!TERRITORY_COORDS.containsKey(id))
                    {
                        response.respond { content = "Territory $id is not managed by the map and cannot be released via this command." }
                        return
                    }

                    val userId = event.interaction.user.id.toString()
                    val isMod = RolePermissionsFunction.isModerator(guildIdVal, event.interaction.user)
                    
                    val ok = if (isMod)
                    {
                        Database.releaseTerritoryByModerator(id, guildIdVal)
                    } else
                    {
                        val userFaction = Database.getFactionOfUser(guildIdVal, userId)
                        val callerOwnerId = userFaction?.name ?: userId
                        val callerOwnerType = if (userFaction != null) "faction" else "user"
                        Database.releaseTerritory(id, callerOwnerId, guildIdVal, callerOwnerType)
                    }
                    
                    if (!ok)
                    {
                        response.respond { content = "Failed to release territory $id. It may not be owned by you (or your faction) or may not exist." }
                        return
                    }

                    try
                    {
                        val outPath = renderFullMapWithClaims(guildIdVal)
                        val f = File(outPath)
                        response.respond {
                            content = "Territory $id released and map updated."
                            addFile(f.toPath())
                        }
                    } catch (e: Exception)
                    {
                        println("TerritoryFunction.execute: no base map found or failed generating map after release for id $id: ${e.message}")
                        e.printStackTrace()
                        response.respond { content = "Territory released, but something went wrong with map generation." }
                    }
                }

                "quest" -> {
                    val idText = event.interaction.command.strings["id"]?.trim().orEmpty()
                    val nameOpt = event.interaction.command.strings["name"]?.trim()
                    val id = idText.toIntOrNull()
                    if (id == null)
                    {
                        response.respond { content = "Usage: /territory quest id:<number> [name:optional]" }
                        return
                    }

                    if (!TERRITORY_COORDS.containsKey(id))
                    {
                        response.respond { content = "Territory $id is not managed by the map." }
                        return
                    }

                    // Only moderators can use this
                    val isMod = RolePermissionsFunction.isModerator(guildIdVal, event.interaction.user)
                    if (!isMod)
                    {
                        response.respond { content = "Only moderators can assign quest territories." }
                        return
                    }

                    val ok = Database.claimTerritory(id, "Questlocked", guildIdVal, nameOpt, "Quest", "quest")
                    if (!ok)
                    {
                        response.respond { content = "Failed to claim territory $id as a quest. It may already be owned by someone else." }
                        return
                    }

                    try
                    {
                        val outPath = renderFullMapWithClaims(guildIdVal)
                        val f = File(outPath)
                        response.respond {
                            content = "Territory $id claimed as Quest and map updated."
                            addFile(f.toPath())
                        }
                    } catch (e: Exception)
                    {
                        println("TerritoryFunction.execute: failed generating map after quest claim for id $id: ${e.message}")
                        e.printStackTrace()
                        response.respond { content = "Territory claimed as quest, but something went wrong with map generation." }
                    }
                }

                "challenge" -> {
                    val idText = event.interaction.command.strings["id"]?.trim().orEmpty()
                    val id = idText.toIntOrNull()
                    if (id == null)
                    {
                        response.respond { content = "Usage: /territory challenge id:<number>" }
                        return
                    }

                    if (!TERRITORY_COORDS.containsKey(id))
                    {
                        response.respond { content = "Territory $id is not managed by the map." }
                        return
                    }

                    val ok = Database.challengeTerritory(id, guildIdVal)
                    if (!ok)
                    {
                        response.respond { content = "Failed to challenge territory $id." }
                        return
                    }

                    try
                    {
                        val outPath = renderFullMapWithClaims(guildIdVal)
                        val f = File(outPath)
                        response.respond {
                            content = "Territory $id marked as challenged and map updated."
                            addFile(f.toPath())
                        }
                    } catch (e: Exception)
                    {
                        println("TerritoryFunction.execute: failed generating map after challenge for id $id: ${e.message}")
                        e.printStackTrace()
                        response.respond { content = "Territory challenged, but something went wrong with map generation." }
                    }
                }

                "rename" -> {
                    val idText = event.interaction.command.strings["id"]?.trim().orEmpty()
                    val newName = event.interaction.command.strings["name"]?.trim().orEmpty()
                    val id = idText.toIntOrNull()
                    if (id == null || newName.isBlank())
                    {
                        response.respond { content = "Usage: /territory rename id:<number> name:<new name>" }
                        return
                    }
                    if (!TERRITORY_COORDS.containsKey(id))
                    {
                        response.respond { content = "Territory $id is not managed by the map and cannot be renamed via this command." }
                        return
                    }
                    val userId = event.interaction.user.id.toString()
                    val userFaction = Database.getFactionOfUser(guildIdVal, userId)
                    
                    // Determine caller owner type and ID
                    val callerOwnerId = userFaction?.name ?: userId
                    val callerOwnerType = if (userFaction != null) "faction" else "user"
                    
                    val ok = Database.renameTerritory(id, callerOwnerId, guildIdVal, newName, callerOwnerType)
                    if (!ok)
                    {
                        response.respond { content = "Failed to rename territory $id. Ensure you own it (or are in the faction that owns it) and the new name is unique (case-insensitive)." }
                        return
                    }
                    response.respond {
                        content = "Territory $id renamed to '$newName'."
                    }
                }

                "list" -> {
                    val list = Database.listTerritories(guildIdVal)
                    if (list.isEmpty())
                    {
                        response.respond { content = "No territories registered yet." }
                        return
                    }
                    val sb = StringBuilder()
                    for (t in list)
                    {
                        val ownerDesc = if (t.ownerId == null) "unowned" else try
                        {
                            if (t.ownerType == "faction")
                            {
                                "${"Faction:".bold()} ${t.ownerId} ${"Color:".bold()} ${(t.color ?: "White")}"
                            }
                            else if (t.ownerType == "user")
                            {
                                "${"Owner:".bold()} ${
                                    getMemberName(
                                        kord,
                                        event.interaction.data.guildId.value,
                                        Snowflake(t.ownerId)
                                    )
                                } ${"Color:".bold()} ${(t.color ?: "White")}"
                            }
                            else
                            {
                                "Questlocked".bold()
                            }
                        } catch (e: Exception)
                        {
                            println("TerritoryFunction.list: display failed: ${e.message}")
                            e.printStackTrace()
                            "${"Owner:".bold()} ${t.ownerId} ${if (t.ownerType != null) "(${t.ownerType})" else ""} ${"Color:".bold()} ${(t.color ?: "White")}"
                        }
                        sb.append("ID ${t.id}: ${t.name} - $ownerDesc\n")
                    }
                    response.respond { content = sb.toString() }
                }

                "map" -> {
                    try
                    {
                        val outPath = renderFullMapWithClaims(guildIdVal)
                        val f = File(outPath)
                        response.respond {
                            content = "Map generated."
                            addFile(f.toPath())
                        }
                    } catch (e: Exception)
                    {
                        println("TerritoryFunction.execute.map failed: ${e.message}")
                        e.printStackTrace()
                        response.respond { content = "Failed to generate map: ${e.message}" }
                    }
                }

                "update" -> {
                    try
                    {
                        val updateCount = updateTerritoryColors(guildIdVal)
                        val outPath = renderFullMapWithClaims(guildIdVal)
                        val f = File(outPath)
                        response.respond {
                            content = "Updated $updateCount territories based on alignment and map regenerated."
                            addFile(f.toPath())
                        }
                    } catch (e: Exception)
                    {
                        println("TerritoryFunction.execute.update failed: ${e.message}")
                        e.printStackTrace()
                        response.respond { content = "Failed to update territories: ${e.message}" }
                    }
                }

                else -> {
                    response.respond { content = "Unknown subcommand. Use claim/release/challenge/rename/list/map." }
                }
            }
        } catch (e: Exception)
        {
            println("TerritoryFunction.execute: unexpected error: ${e.message}")
            e.printStackTrace()
            response.respond { content = "An internal error occurred while handling the territory command." }
        }
    }

    /**
     * Produces a full map with all claimed territories colored for the given guild
     * @throws IllegalArgumentException - If the base map resource is not found or cannot be read.
     */
    private fun renderFullMapWithClaims(guildId: String): String
    {
        val stream = javaClass.getResourceAsStream("/SmallHavenMap.png")
            ?: throw IllegalArgumentException("Resource /SmallHavenMap.png not found.")
        val img = ImageIO.read(stream) ?: throw IllegalArgumentException("Could not read image resource.")

        val territories = Database.listTerritories(guildId)
        for (t in territories)
        {
            val coord = TERRITORY_COORDS[t.id]
            val color = t.color
            if (coord != null && color != null)
            {
                val hexColor = getHexForColor(color) ?: color
                floodFillBounded(img, coord.first, coord.second, parseHexColor(hexColor))
            }
        }

        val outDir = File("db/maps")
        if (!outDir.exists()) outDir.mkdirs()
        val out = File(outDir, "SmallHavenMap_all_claims_${guildId}.png")
        ImageIO.write(img, "PNG", out)
        return out.absolutePath
    }

    /**
     * Convert color name to hex for rendering (#RRGGBB).
     */
    private fun getHexForColor(color: String): String?
    {
        return when (color)
        {
            "White" -> COLOR_UNCLAIMED
            "Yellow" -> COLOR_GOOD
            "Purple" -> COLOR_EVIL
            "Turquoise" -> COLOR_QUEST
            "DarkGray" -> COLOR_CHALLENGED
            else -> null
        }
    }

    /**
     * Update all territories' colors based on their owner's alignment.
     * Skips Quest, Challenged, and unowned territories.
     * Returns the count of updated territories.
     */
    private fun updateTerritoryColors(guildId: String): Int
    {
        val territories = Database.listTerritories(guildId)
        var updateCount = 0

        for (t in territories)
        {
            // Skip unowned territories, quest territories, and challenged territories
            if (t.ownerId == null || t.color == COLOR_QUEST || t.color == COLOR_CHALLENGED)
            {
                continue
            }

            val newColor = try
            {
                when (t.ownerType)
                {
                    "user" -> {
                        val alignment = Database.getAlignment(guildId, t.ownerId)
                        when (alignment?.intent)
                        {
                            "Good" -> "Yellow"
                            "Evil" -> "Purple"
                            else -> continue
                        }
                    }
                    "faction" -> {
                        val faction = Database.getFaction(guildId, t.ownerId)
                        faction?.color ?: throw IllegalArgumentException("Faction ${t.ownerId} does not exist.")
                    }
                    else -> continue
                }
            } catch (e: Exception)
            {
                println("TerritoryFunction.updateTerritoryColors: error determining color for territory ${t.id}: ${e.message}")
                continue
            }

            // Update the territory color if it changed
            if (newColor != t.color)
            {
                val success = Database.updateTerritoryColor(t.id, guildId, newColor)
                if (success)
                {
                    updateCount++
                }
            }
        }

        return updateCount
    }

    // #RRGGBB -> ARGB int
    private fun parseHexColor(hex: String): Int
    {
        val h = hex.trim().let { if (it.startsWith("#")) it.substring(1) else it }
        val rgb = Integer.parseInt(h, 16) and 0xFFFFFF
        return 0xFF000000.toInt() or rgb
    }

    /**
     * Flood fill algorithm that fills a region in the image starting from (sx, sy) with the replacement color, bounded by black pixels.
     */
    private fun floodFillBounded(img: BufferedImage, sx: Int, sy: Int, replacementArgb: Int)
    {
        val w = img.width
        val h = img.height
        if (sx !in 0 until w || sy !in 0 until h) throw IllegalArgumentException("Start coordinate out of bounds: $sx,$sy for image $w x $h")

        val borderArgb = Color.BLACK.rgb                // Black is border color
        val targetColor = img.getRGB(sx, sy)
        if (targetColor == replacementArgb) return

        val visited = Array(w) { BooleanArray(h) }
        val dq = ArrayDeque<Pair<Int, Int>>()
        dq.addLast(sx to sy)

        while (dq.isNotEmpty())
        {
            val (x, y) = dq.removeFirst()
            if (x < 0 || x >= w || y < 0 || y >= h) continue
            if (visited[x][y]) continue
            visited[x][y] = true
            val col = img.getRGB(x, y)

            if (col == borderArgb) continue     // Never paint border
            if (col != targetColor) continue    // Only paint matching pixels

            img.setRGB(x, y, replacementArgb)

            dq.addLast(x + 1 to y)
            dq.addLast(x - 1 to y)
            dq.addLast(x to y + 1)
            dq.addLast(x to y - 1)
        }
    }
}
