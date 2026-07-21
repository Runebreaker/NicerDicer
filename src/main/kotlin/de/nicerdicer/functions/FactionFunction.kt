package de.nicerdicer.functions

import de.nicerdicer.db.Database
import de.nicerdicer.util.KordUtil
import de.nicerdicer.util.KtorUtils
import de.nicerdicer.util.bold
import dev.kord.common.Color
import dev.kord.common.entity.DiscordSelectOption
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.TextInputStyle
import dev.kord.common.entity.optional.OptionalBoolean
import dev.kord.core.behavior.createRole
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ActionInteractionCreateEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.message.addFile
import dev.kord.rest.builder.message.embed
import java.io.File
import kotlin.text.isNotEmpty
import kotlin.text.trim

object FactionFunction : FunctionBase("faction", "Everything about factions.")
{
    override val modalHandlers: Map<String, suspend (ActionInteractionCreateEvent) -> Unit> = mapOf(
        "faction_builder" to { handleFactionBuilder(it as GuildModalSubmitInteractionCreateEvent) },
        "faction_update_" to { handleFactionUpdate(it as GuildModalSubmitInteractionCreateEvent) },
        "members_update_" to { handleMembersUpdate(it as GuildModalSubmitInteractionCreateEvent) },
        "image_update_" to { handleImageUpdate(it as GuildModalSubmitInteractionCreateEvent) }
    )

    override suspend fun defineLayout(builder: ChatInputCreateBuilder)
    {
        builder.apply {
            subCommand("create", "Create a new faction")
            subCommand("update", "Update faction description and/or image") {
                string("name", "Faction name") { required = true }
            }
            subCommand("delete", "Delete a faction (owner or mod only)") {
                string("name", "Faction name") { required = true }
            }
            subCommand("get", "Show faction information") {
                string("name", "Faction name") { required = true }
            }
            subCommand("members", "Update the member list.") {
                string("name", "Faction name") { required = true }
            }
            subCommand("image", "Update faction image") {
                string("name", "Faction name") { required = true }
            }
            subCommand("list", "List all factions in this guild")
        }

        Database.init()
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val guildIdVal = event.interaction.data.guildId.value?.toString()
        val guild = guildIdVal?.let { event.kord.getGuild(Snowflake(it)) }

        if (guildIdVal == null || guild == null)
        {
            event.interaction.respondEphemeral { content = "This command can only be used in a guild." }
            return
        }

        try
        {
            val subCommand = event.interaction.command.data.options.value?.map { it.name }?.first() ?: "list"
            val modRoleName = KordUtil.getModRoleOrNull(guild)?.name?.bold() ?: "a moderator".bold()

            when (subCommand)
            {
                "create" ->
                {
                    if (!KordUtil.isModerator(event))
                    {
                        event.interaction.respondEphemeral {
                            content = "Only moderators can create a new faction. Ping $modRoleName to create your faction."
                        }
                        return
                    }
                    handleCreate(event)
                }

                "update" ->
                {
                    if (!KordUtil.isModerator(event))
                    {
                        event.interaction.respondEphemeral {
                            content = "Only moderators can update a faction. Ping $modRoleName to update your faction."
                        }
                    }
                    handleUpdate(event, guildIdVal)
                }

                "delete" ->
                {
                    if (!KordUtil.isModerator(event))
                    {
                        event.interaction.respondEphemeral {
                            content = "Only moderators can delete a faction. Ping $modRoleName to delete your faction."
                        }
                    }
                    handleDelete(event, guildIdVal)
                }

                "members" ->
                {
                    if (!KordUtil.isModerator(event))
                    {
                        event.interaction.respondEphemeral {
                            content = "Only moderators can edit faction members. Ping $modRoleName to add/remove members."
                        }
                    }
                    handleMembers(event, guildIdVal)
                }

                "image" ->
                {
                    if (!KordUtil.isModerator(event))
                    {
                        event.interaction.respondEphemeral {
                            content = "Only moderators can update faction images. Ping $modRoleName to update your faction image."
                        }
                    }
                    handleImageUpdate(event, guildIdVal)
                }

                "get" -> handleGet(event, guildIdVal)
                "list" -> handleList(event, guildIdVal)
            }
        } catch (e: Exception)
        {
            println("FactionFunction.execute failed: ${e.message}")
            e.printStackTrace()
            event.interaction.respondEphemeral { content = "An error occurred while processing your command." }
        }
    }

    private suspend fun handleCreate(event: ChatInputCommandInteractionCreateEvent)
    {
        event.interaction.modal("Faction Builder", "faction_builder") {
            label("Faction Name") {
                textInput(TextInputStyle.Short, "name_input") {
                    required = true
                    placeholder = "Type in your faction name here..."
                }
            }
            label("Description") {
                textInput(TextInputStyle.Paragraph, "description_input") {
                    required = false
                    placeholder = "Type in your faction description here..."
                }
            }
            label("Color") {
                textInput(TextInputStyle.Short, "color_input") {
                    required = true
                    placeholder = "Faction color in hex format (e.g., FF0000)"
                }
            }
            label("Owner") {
                userSelect("owner_select") {
                    placeholder = "Select the owner of this faction..."
                    allowedValues = 1..1
                }
            }
            label("Alignment") {
                radioGroup("alignment_select") {
                    required = true
                    options = listOf(DiscordSelectOption("Good", "Good", default = OptionalBoolean.Value(true)), DiscordSelectOption("Evil", "Evil"))
                }
            }
        }
    }

    private suspend fun handleUpdate(event: ChatInputCommandInteractionCreateEvent, guildIdVal: String)
    {
        val factionName = event.interaction.command.strings["name"] ?: ""

        if (factionName.isBlank())
        {
            event.interaction.respondEphemeral { content = "No name given. Try /faction update <name>" }
            return
        }

        val factionToUpdate = Database.getFaction(guildIdVal, factionName)

        if (factionToUpdate == null)
        {
            event.interaction.respondEphemeral { content = "No guild with name $factionName found." }
            return
        }

        event.interaction.modal("Faction Update: ${factionToUpdate.name}", "faction_update_${factionToUpdate.name}") {
            label("Description") {
                textInput(TextInputStyle.Paragraph, "description_input") {
                    required = false
                    placeholder = "Type in your new faction description here (leave empty to keep current)..."
                    value = factionToUpdate.description
                }
            }
            label("Color") {
                textInput(TextInputStyle.Short, "color_input") {
                    required = true
                    placeholder = "Faction color in hex format (e.g., FF0000)"
                    value = factionToUpdate.color
                }
            }
            label("Owner") {
                userSelect("owner_select") {
                    placeholder = "Select the owner of this faction..."
                    allowedValues = 1..1
                    defaultUsers.add(Snowflake(factionToUpdate.factionOwnerId))
                }
            }
            label("Alignment") {
                radioGroup("alignment_select") {
                    required = true
                    options = listOf(
                        DiscordSelectOption("Good", "Good", default = OptionalBoolean.Value(factionToUpdate.alignment == "Good")),
                        DiscordSelectOption("Evil", "Evil", default = OptionalBoolean.Value(factionToUpdate.alignment == "Evil"))
                    )
                }
            }
        }
    }

    private suspend fun handleDelete(event: ChatInputCommandInteractionCreateEvent, guildIdVal: String)
    {
        val factionName = (event.interaction.command.strings["name"] ?: "").trim()
        if (factionName.isBlank())
        {
            event.interaction.respondEphemeral { content = "Faction name cannot be empty." }
            return
        }

        val faction = Database.getFaction(guildIdVal, factionName)
        if (faction == null)
        {
            event.interaction.respondEphemeral { content = "Faction ${factionName.bold()} not found." }
            return
        }

        val deleted = Database.deleteFaction(guildIdVal, factionName)

        if (!deleted)
        {
            event.interaction.respondEphemeral { content = "Failed to delete faction ${factionName.bold()}." }
            return
        }

        faction.image?.let {
            val outDir = File("db/factions")
            if (!outDir.exists()) outDir.mkdirs()
            val out = File(outDir, "${factionName}_icon.png")

            println("Deleting image for faction $factionName...")
            try
            {
                out.delete()
            } catch (e: Exception)
            {
                println("FactionFunction.handleDelete: could not delete image for faction $factionName: ${e.message}")
                event.interaction.respondEphemeral { content = "An internal error occurred." }
                return
            }
        }

        // Try to delete the associated Discord role
        try
        {
            val guild = event.kord.getGuild(Snowflake(guildIdVal))
            val role = guild.getRoleOrNull(Snowflake(faction.factionRoleId))
            role?.delete("Faction deleted.")
        } catch (e: Exception)
        {
            println("FactionFunction.handleDelete: could not delete role ${faction.factionRoleId}: ${e.message}")
            event.interaction.respondEphemeral { content = "An internal error occurred." }
            return
        }

        event.interaction.respondEphemeral { content = "Faction ${factionName.bold()} and its associated role have been deleted." }
    }

    private suspend fun handleMembers(event: ChatInputCommandInteractionCreateEvent, guildIdVal: String)
    {
        val factionName = (event.interaction.command.strings["name"] ?: "").trim()
        if (factionName.isBlank())
        {
            event.interaction.respondEphemeral { content = "Faction name cannot be empty." }
            return
        }

        val faction = Database.getFaction(guildIdVal, factionName)
        if (faction == null)
        {
            event.interaction.respondEphemeral { content = "Faction ${factionName.bold()} not found." }
            return
        }

        val members = faction.memberList.split(",").map { Snowflake(it.trim()) }

        event.interaction.modal("Members Update: $factionName", "members_update_$factionName") {
            label("Members") {
                userSelect("members_select") {
                    allowedValues = 1..25
                    defaultUsers.addAll(members)
                }
            }
        }
    }

    private suspend fun handleImageUpdate(event: ChatInputCommandInteractionCreateEvent, guildIdVal: String)
    {
        val factionName = (event.interaction.command.strings["name"] ?: "").trim()
        if (factionName.isBlank())
        {
            event.interaction.respondEphemeral { content = "Faction name cannot be empty." }
            return
        }

        val faction = Database.getFaction(guildIdVal, factionName)
        if (faction == null)
        {
            event.interaction.respondEphemeral { content = "Faction ${factionName.bold()} not found." }
            return
        }

        event.interaction.modal("Image Update: $factionName", "image_update_$factionName") {
            label("Image") {
                fileUpload("image_file") {
                    minValues = 0
                    maxValues = 1
                    required = false
                }
            }
        }
    }

    private suspend fun handleGet(event: ChatInputCommandInteractionCreateEvent, guildIdVal: String)
    {
        val response = event.interaction.deferPublicResponse()

        val factionName = (event.interaction.command.strings["name"] ?: "").trim()
        if (factionName.isBlank())
        {
            response.respond { content = "Faction name cannot be empty." }
            return
        }

        val faction = Database.getFaction(guildIdVal, factionName)
        if (faction == null)
        {
            response.respond { content = "Faction ${factionName.bold()} not found." }
            return
        }

        val owner = try
        {
            event.kord.getUser(Snowflake(faction.factionOwnerId))?.username ?: "Unknown"
        } catch (e: Exception)
        {
            println("FactionFunction.handleGet: could not fetch owner user ${faction.factionOwnerId}: ${e.message}")
            "Unknown"
        }

        val members = faction.memberList.split(",").filter { it.isNotEmpty() }

        val imagePath = faction.image?.let { File(it).toPath() }

        response.respond {
            embed {
                title = faction.name
                description = faction.description
                color = try
                {
                    Color(faction.color.removePrefix("#").toInt(16))
                } catch (e: Exception)
                {
                    println("FactionFunction.handleGet: error when parsing faction color ${faction.color}: ${e.message}")
                    Color(128, 128, 128)  // default gray
                }

                field {
                    name = "Owner"
                    value = owner
                    inline = true
                }
                field {
                    name = "Color"
                    value = faction.color
                    inline = true
                }
                field {
                    name = "Members"
                    value = if (members.isEmpty()) "None" else members.size.toString()
                    inline = true
                }

                imagePath?.let {
                    image = "attachment://${it.fileName}"
                }
            }
            imagePath?.let { addFile(it) }
        }
    }

    private suspend fun handleList(event: ChatInputCommandInteractionCreateEvent, guildIdVal: String)
    {
        val response = event.interaction.deferPublicResponse()

        val factions = Database.listFactions(guildIdVal)

        if (factions.isEmpty())
        {
            response.respond { content = "No factions exist in this guild yet." }
            return
        }

        val factionList = factions
            .map { Pair(it.name, KordUtil.getMemberName(event.kord, Snowflake(guildIdVal), Snowflake(it.factionOwnerId))) }
            .joinToString("\n") { (name, owner) ->
                "• **$name** (Owner: $owner)"
            }

        response.respond {
            content = factionList
        }
    }

    private suspend fun handleFactionBuilder(event: GuildModalSubmitInteractionCreateEvent)
    {
        val guildIdVal = event.interaction.guildId.value.toString()
        val inputs = event.interaction.textInputs

        val factionName = inputs["name_input"]?.value?.trim() ?: ""
        val description = inputs["description_input"]?.value?.trim() ?: ""
        var colorHex = inputs["color_input"]?.value?.trim() ?: ""
        val factionOwnerId = event.interaction.userSelects["owner_select"]?.valueIds?.firstOrNull()?.value?.toString()
        val alignment = event.interaction.radioGroups["alignment_select"]?.value?.trim() ?: ""

        if (factionOwnerId == null)
        {
            event.interaction.respondEphemeral { content = "A Faction needs an owner! Please select a user to be the owner of this faction." }
            return
        }

        if (factionName.isBlank())
        {
            event.interaction.respondEphemeral { content = "Faction name cannot be empty." }
            return
        }

        // Check if faction already exists
        Database.getFaction(guildIdVal, factionName)?.let {
            event.interaction.respondEphemeral { content = "A faction with the name ${factionName.bold()} already exists." }
            return
        }

        // Validate and normalize hex color
        colorHex = colorHex.removePrefix("#").uppercase()
        if (colorHex.length != 6 || !colorHex.all { it in '0'..'9' || it in 'A'..'F' })
        {
            event.interaction.respondEphemeral { content = "Invalid hex color format. Use 6 hex digits (e.g., FF0000)." }
            return
        }
        colorHex = "#$colorHex"

        // Create the Discord role with the faction color
        val guild = event.interaction.guild

        try
        {
            val color = Color(colorHex.removePrefix("#").toInt(16))
            val role = guild.createRole {
                name = factionName
                this.color = color
            }

            // Create faction in database
            val created = Database.createFaction(
                guildId = guildIdVal,
                name = factionName,
                ownerId = factionOwnerId,
                roleId = role.id.toString(),
                description = description,
                color = colorHex,
                alignment = alignment
            )

            if (created)
            {
                event.interaction.respondEphemeral {
                    content = "Faction ${factionName.bold()} created successfully with role and color!"
                }
            } else
            {
                role.delete()
                event.interaction.respondEphemeral { content = "Failed to create faction in database." }
            }
        } catch (e: Exception)
        {
            println("FactionFunction.handleCreate: error creating role or faction: ${e.message}")
            e.printStackTrace()
            event.interaction.respondEphemeral { content = "Failed to create faction: ${e.message}" }
        }
    }

    private suspend fun handleFactionUpdate(event: GuildModalSubmitInteractionCreateEvent)
    {
        val guildIdVal = event.interaction.guildId.value.toString()
        val inputs = event.interaction.textInputs

        val factionName = event.interaction.modalId.removePrefix("faction_update_")
        val description = inputs["description_input"]?.value?.trim() ?: ""
        var colorHex = inputs["color_input"]?.value?.trim() ?: ""
        val factionOwnerId = event.interaction.userSelects["owner_select"]?.valueIds?.firstOrNull()?.value?.toString()
        val alignment = event.interaction.radioGroups["alignment_select"]?.value?.trim() ?: ""

        val faction = Database.getFaction(guildIdVal, factionName)
        if (faction == null)
        {
            event.interaction.respondEphemeral { content = "Faction ${factionName.bold()} not found." }
            return
        }

        if (factionOwnerId == null)
        {
            event.interaction.respondEphemeral {
                content = "A Faction needs an owner! Please select a user to be the owner of this faction."
            }
            return
        }

        val imageSnowflake = event.interaction.fileUploads["image_file"]?.valueIds?.firstOrNull()

        val imageUrl = event.interaction.data.data.resolvedObjectsData.value?.attachments?.value?.get(imageSnowflake)?.url

        if (factionName.isBlank())
        {
            event.interaction.respondEphemeral { content = "Faction name cannot be empty. This should never occur in update." }
            return
        }

        val outDir = File("db/factions")
        if (!outDir.exists()) outDir.mkdirs()
        val out = File(outDir, "${factionName}_icon.png")

        imageUrl?.let {
            println("Updating image for faction $factionName...")
            KtorUtils.downloadImage(it, out)
        } ?: out.delete()

        // Validate and normalize hex color
        colorHex = colorHex.removePrefix("#").uppercase()
        if (colorHex.length != 6 || !colorHex.all { it in '0'..'9' || it in 'A'..'F' })
        {
            event.interaction.respondEphemeral { content = "Invalid hex color format. Use 6 hex digits (e.g., FF0000)." }
            return
        }
        colorHex = "#$colorHex"

        val guild = event.interaction.guild

        try
        {
            val color = Color(colorHex.removePrefix("#").toInt(16))
            val roleId = Database.getFaction(guildIdVal, factionName)?.factionRoleId?.let { Snowflake(it) }
            roleId?.let { guild.getRole(it) }?.edit { this.color = color } ?: println("Could not find role for faction $factionName to update color.")

            // Update faction in database
            val updated = Database.updateFaction(
                guildId = guildIdVal,
                name = factionName,
                ownerId = factionOwnerId,
                newDescription = description,
                newImage = if (out.exists()) out.path else null,
                newColor = colorHex,
                newMemberList = faction.memberList,
                alignment = alignment
            )

            if (updated)
            {
                event.interaction.respondEphemeral {
                    content = "Faction ${factionName.bold()} updated successfully!"
                }
            } else
            {
                event.interaction.respondEphemeral { content = "Failed to update faction in database." }
            }
        } catch (e: Exception)
        {
            println("FactionFunction.handleCreate: error creating role or faction: ${e.message}")
            e.printStackTrace()
            event.interaction.respondEphemeral { content = "Failed to create faction: ${e.message}" }
        }
    }

    private suspend fun handleMembersUpdate(event: GuildModalSubmitInteractionCreateEvent)
    {
        val guildIdVal = event.interaction.guildId.value.toString()
        val factionName = event.interaction.modalId.removePrefix("members_update_")
        val faction = Database.getFaction(guildIdVal, factionName)
        if (faction == null)
        {
            event.interaction.respondEphemeral { content = "Faction ${factionName.bold()} not found." }
            return
        }

        val selectedMembers = event.interaction.userSelects["members_select"]?.valueIds?.map { it.value.toString() } ?: emptyList()
        val memberListStr = selectedMembers.joinToString(",")

        val updated = Database.updateFaction(
            guildId = guildIdVal,
            name = factionName,
            ownerId = faction.factionOwnerId,
            newDescription = faction.description,
            newImage = faction.image,
            newColor = faction.color,
            newMemberList = memberListStr,
            alignment = faction.alignment
        )

        if (updated)
        {
            event.interaction.respondEphemeral {
                content = "Members for faction ${factionName.bold()} updated successfully!"
            }
        } else
        {
            event.interaction.respondEphemeral { content = "Failed to update members for faction ${factionName.bold()}." }
        }
    }

    private suspend fun handleImageUpdate(event: GuildModalSubmitInteractionCreateEvent)
    {
        val guildIdVal = event.interaction.guildId.value.toString()
        val factionName = event.interaction.modalId.removePrefix("image_update_")
        val faction = Database.getFaction(guildIdVal, factionName)
        if (faction == null)
        {
            event.interaction.respondEphemeral { content = "Faction ${factionName.bold()} not found." }
            return
        }

        val imageSnowflake = event.interaction.fileUploads["image_file"]?.valueIds?.firstOrNull()
        val imageUrl = event.interaction.data.data.resolvedObjectsData.value?.attachments?.value?.get(imageSnowflake)?.url

        val outDir = File("db/factions")
        if (!outDir.exists()) outDir.mkdirs()
        val out = File(outDir, "${factionName}_icon.png")

        imageUrl?.let {
            println("Updating image for faction $factionName...")
            KtorUtils.downloadImage(it, out)
        } ?: out.delete()

        val updated = Database.updateFaction(
            guildId = guildIdVal,
            name = factionName,
            ownerId = faction.factionOwnerId,
            newDescription = faction.description,
            newImage = if (out.exists()) out.path else null,
            newColor = faction.color,
            newMemberList = faction.memberList,
            alignment = faction.alignment
        )

        if (updated)
        {
            event.interaction.respondEphemeral {
                content = "Image for faction ${factionName.bold()} updated successfully!"
            }
        } else
        {
            event.interaction.respondEphemeral { content = "Failed to update image for faction ${factionName.bold()}." }
        }
    }
}