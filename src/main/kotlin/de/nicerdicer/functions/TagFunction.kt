package de.nicerdicer.functions

import de.nicerdicer.db.Database
import dev.kord.common.entity.TextInputStyle
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.GuildModalSubmitInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand

object TagFunction : FunctionBase("tag", "Show given tag.")
{
    override suspend fun prepare(kord: Kord)
    {
        // register command with subcommands: create, edit, delete, get, list
        kord.createGlobalChatInputCommand(name, description) {
            subCommand("create", "Create a new tag") {
                string("name", "Tag name") { required = false }
                string("content", "Tag content") { required = false }
            }
            subCommand("edit", "Edit an existing tag (owner only)") {
                string("name", "Tag name") { required = false }
                string("content", "New tag content") { required = false }
            }
            subCommand("delete", "Delete a tag (owner only)") {
                string("name", "Tag name") { required = true }
            }
            subCommand("get", "Show a tag") {
                string("name", "Tag name") { required = true }
            }
            subCommand("list", "List your tags")
        }

        kord.on<GuildModalSubmitInteractionCreateEvent> {
            handleTagModal(this)
        }

        Database.init()
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        try
        {
            val subCommand = event.interaction.command.data.options.value?.map { it.name }?.first() ?: "get"

            when (subCommand)
            {
                "create" ->
                {
                    val name = (event.interaction.command.strings["name"] ?: "").trim()
                    val responseContent = event.interaction.command.strings["content"] ?: ""
                    if (name.isBlank() || responseContent.isBlank())
                    {
                        event.interaction.modal("Create Tag", "create_tag") {
                            label("Tag name") {
                                textInput(TextInputStyle.Short, "tag_name") {
                                    required = true
                                    placeholder = "Insert you tag name here!"
                                }
                            }
                            label("Tag content") {
                                textInput(TextInputStyle.Paragraph, "tag_content") {
                                    required = true
                                    placeholder = "Insert your tag content here!"
                                }
                            }
                        }
                        return
                    }
                    val ownerId = event.interaction.user.id.toString()
                    val ok = Database.createTag(name, ownerId, responseContent)
                    if (ok) event.interaction.respondPublic { content = "Tag '$name' created." }
                    else event.interaction.respondPublic { content = "Failed to create tag '$name' — it may already exist (case-insensitive) or an error occurred." }
                }

                "edit" ->
                {
                    val name = (event.interaction.command.strings["name"] ?: "").trim()
                    val responseContent = event.interaction.command.strings["content"] ?: ""
                    val ownerId = event.interaction.user.id.toString()
                    if (name.isBlank() || responseContent.isBlank())
                    {
                        val existingTag = name.takeIf { it.isNotBlank() }?.let { Database.getTag(it) }
                        if (name.isNotBlank() && (existingTag == null || existingTag.owner != ownerId))
                        {
                            event.interaction.respondEphemeral { content = "Failed to update tag '$name' - it might not exist or you might not be the owner." }
                            return
                        }
                        val modalName = existingTag?.name ?: name
                        val modalContent = existingTag?.content ?: responseContent
                        event.interaction.modal("Edit Tag", "edit_tag") {
                            label("Tag name") {
                                textInput(TextInputStyle.Short, "tag_name") {
                                    required = true
                                    placeholder = "Insert your tag name here!"
                                    if (modalName.isNotEmpty()) value = modalName
                                }
                            }
                            label("Tag content") {
                                textInput(TextInputStyle.Paragraph, "tag_content") {
                                    required = true
                                    placeholder = "Insert your new tag content here!"
                                    if (modalContent.isNotEmpty()) value = modalContent
                                }
                            }
                        }
                        return
                    }
                    val response = event.interaction.deferPublicResponse()
                    val ok = Database.updateTag(name, ownerId, responseContent)
                    if (ok) response.respond { content = "Tag '$name' updated." }
                    else response.respond { content = "Failed to update tag '$name' — it might not exist or you might not be the owner." }
                }

                "delete" ->
                {
                    val response = event.interaction.deferPublicResponse()
                    val name = (event.interaction.command.strings["name"] ?: "").trim()
                    if (name.isBlank())
                    {
                        response.respond { content = "Usage: /tag delete name:<name>" }
                        return
                    }
                    val ownerId = event.interaction.user.id.toString()
                    val ok = Database.deleteTag(name, ownerId)
                    if (ok) response.respond { content = "Tag '$name' deleted." }
                    else response.respond { content = "Failed to delete tag '$name' — it might not exist or you might not be the owner." }
                }

                "list" ->
                {
                    val response = event.interaction.deferPublicResponse()
                    val ownerId = event.interaction.user.id.toString()
                    val tags = Database.listTagsByOwner(ownerId)
                    if (tags.isEmpty()) response.respond { content = "You have no tags." }
                    else
                    {
                        val names = tags.joinToString(", ") { it.name }
                        response.respond { content = "Your tags (${tags.size}): $names" }
                    }
                }

                else -> // Default: /tag get
                {
                    val response = event.interaction.deferPublicResponse()
                    val nameInput = (event.interaction.command.strings["name"] ?: subCommand).trim()
                    if (nameInput.isBlank()) {
                        response.respond { content = "Usage: /tag get name:<name>" }
                        return
                    }
                    Database.getTag(nameInput)?.let {
                        response.respond { content = it.content }
                        return
                    }
                    response.respond { content = "Tag '$nameInput' not found." }
                }
            }
        } catch (e: Exception)
        {
            val response = event.interaction.deferPublicResponse()
            println("TagFunction.execute: unexpected error: ${e.message}")
            e.printStackTrace()
            response.respond { content = "An internal error occurred while handling the tag command." }
        }
    }

    private suspend fun handleTagModal(event: GuildModalSubmitInteractionCreateEvent)
    {
        when (event.interaction.modalId) {
            "create_tag" ->
            {
                val name = event.interaction.textInputs["tag_name"]?.value?.trim() ?: ""
                val tagContent = event.interaction.textInputs["tag_content"]?.value ?: ""
                if (name.isBlank() || tagContent.isBlank())
                {
                    event.interaction.respondEphemeral { content = "Tag name and content cannot be empty." }
                    return
                }
                val ownerId = event.interaction.user.id.toString()
                val ok = Database.createTag(name, ownerId, tagContent)
                if (ok) event.interaction.respondEphemeral { content = "Tag '$name' created." }
                else event.interaction.respondEphemeral { content = "Failed to create tag '$name' — it may already exist (case-insensitive) or an error occurred." }
            }

            "edit_tag" ->
            {
                val name = event.interaction.textInputs["tag_name"]?.value?.trim() ?: ""
                val tagContent = event.interaction.textInputs["tag_content"]?.value ?: ""
                if (name.isBlank() || tagContent.isBlank())
                {
                    event.interaction.respondEphemeral { content = "Tag name and content cannot be empty." }
                    return
                }
                val ownerId = event.interaction.user.id.toString()
                val ok = Database.updateTag(name, ownerId, tagContent)
                if (ok) event.interaction.respondEphemeral { content = "Tag '$name' updated." }
                else event.interaction.respondEphemeral { content = "Failed to update tag '$name' - it might not exist or you might not be the owner." }
            }
        }
    }
}
