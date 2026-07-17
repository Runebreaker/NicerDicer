package de.nicerdicer.functions

import de.nicerdicer.util.Card
import de.nicerdicer.util.CardDeck
import de.nicerdicer.util.CardHand
import de.nicerdicer.util.Rank
import de.nicerdicer.util.Suit
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.User
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.SubCommandBuilder
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand

object CardFunction : FunctionBase("cards", "Represents a deck of cards.")
{
    val userDecks = mutableMapOf<User, CardDeck>()
    val userHands = mutableMapOf<User, CardHand>()

    override fun register(registrar: InteractionRegistrar)
    {
        registrar.command(name, description, ::execute) {
            subCommand("prepare", "Prepares a 52 Cards + 2 Jokers deck for the user.")
            subCommand("draw", "Draws a card from the deck.") {
                integer("draw_amount", "How many cards to draw.") {
                    required = false
                }
            }
            subCommand("shuffle", "Shuffle the deck.")
            subCommand("hand", "Show hand.")
            subCommand("insert", "Insert card from hand into the deck.") {
                integer("hand_index", "What card from hand to insert.") {
                    required = true
                }
                integer("deck_index", "Where to insert the card into the deck, from the top.") {
                    required = false
                }
            }
            subCommand("count", "Shows how many cards are left in the deck.")
            subCommand("sort", "Sorts your hand.")
            subCommand("create", "Creates a new card in your hand.") {
                string("rank", "The rank of the card to create.") {
                    required = true
                    for (rank in Rank.entries)
                    {
                        choice("$rank", "$rank")
                    }
                }
                string("suit", "The suit of the card to create.") {
                    required = true
                    for (suit in Suit.entries)
                    {
                        choice("$suit", "$suit")
                    }
                }
            }
            subCommand("destroy", "Destroys cards in your hand.") {
                string("indexes", "The indexes of the cards in your hand to destroy, separated by comma. e.g. /destroy 1,4,5") {
                    required = true
                }
            }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val response = event.interaction.deferPublicResponse()
        val user = event.interaction.user

        val subCommand = event.interaction.command.data.options.value?.map { it.name } ?: emptyList()

        if (subCommand.firstOrNull() == "prepare")
        {
            val newDeck = CardDeck()
            newDeck.shuffle()
            userDecks[user] = newDeck
            userHands[user] = CardHand()

            response.respond {
                content = "Cards prepared!"
            }
        }
        if (!userDecks.containsKey(user))
        {
            response.respond {
                content = "You don't have a deck yet! Try: /prepare"
            }
            return
        }

        val deck = userDecks[user]!!
        val hand = userHands[user]!!

        when (subCommand.firstOrNull())
        {
            "draw" ->
            {
                val drawAmount = event.interaction.command.integers["draw_amount"] ?: 1
                val cardsDrawn = mutableListOf<Card>()

                for (i in 1..drawAmount)
                {
                    val card = deck.draw()
                    card?.let {
                        hand.addCard(it)
                        cardsDrawn.add(it)
                    }
                }

                if (cardsDrawn.size == 1) response.respond {
                    content = "You drew a ${cardsDrawn.first().toLongString()}!"
                }
                else response.respond {
                    content = "You drew ${cardsDrawn.size} cards: $cardsDrawn"
                }
            }

            "shuffle" ->
            {
                deck.shuffle()

                response.respond {
                    content = "You have shuffled cards!"
                }
            }

            "hand" ->
            {
                response.respond {
                    content = "Your Hand: ${hand.getHand()}"
                }
            }

            "insert" ->
            {
                val integers = event.interaction.command.integers
                val card = hand.removeCardAt(integers["hand_index"]?.toInt() ?: 0)
                card?.let {
                    deck.insertCardAt(integers["deck_index"]?.toInt() ?: 0, card)
                    response.respond {
                        content = "Card inserted!"
                    }
                } ?: response.respond {
                    content = "Couldn't find card at index!"
                }
            }

            "count" ->
            {
                response.respond {
                    content = "Cards left: ${deck.cards.size}"
                }
            }

            "sort" ->
            {
                hand.sort()
                response.respond {
                    content = "Cards sorted!"
                }
            }

            "create" ->
            {
                val rankToCreate = Rank.valueOf(event.interaction.command.strings["rank"]!!)
                val suitToCreate = Suit.valueOf(event.interaction.command.strings["suit"]!!)

                val newCard = Card(suitToCreate, rankToCreate)

                hand.addCard(newCard)

                response.respond {
                    content = "Card ${newCard.toLongString()} added!"
                }
            }

            "destroy" ->
            {
                val indexesToDestroy = event.interaction.command.strings["indexes"]!!.trim().split(",").map { it.toInt() }.sortedDescending()
                val removedCards = mutableListOf<Card>()

                for (index in indexesToDestroy)
                {
                    hand.removeCardAt(index - 1)?.let { removedCard -> removedCards.add(removedCard) }
                }

                response.respond {
                    content = "Cards destroyed: $removedCards"
                }
            }
        }
    }
}
