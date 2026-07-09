package de.nicerdicer.functions

import de.nicerdicer.util.RollResult
import de.nicerdicer.util.italic
import de.nicerdicer.util.stricken
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.integer
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.rest.builder.interaction.user

object CombatFunction : FunctionBase("combat", "Everything relating to combat.")
{
    var kord: Kord? = null
    val trackedCombats = mutableMapOf<Channel, Combat>()

    override suspend fun prepare(kord: Kord)
    {
        this.kord = kord
        kord.createGlobalChatInputCommand(name, description) {
            subCommand("start", "Starts combat!")
            subCommand("finish", "Finish combat!")
            subCommand("leave", "Leave combat.")
            subCommand("init", "Sets initiative and roll type.") {
                integer("result", "Initiative result.") { required = true }
                string("roll", "String for what roll is used; e.g. 3d20+4") { required = true }
            }
            subCommand("initiative", "Rolls init!") {
                integer("amount", "Amount of dice to roll, important for SW etc.") {
                    required = false
                }
                integer("modifier", "Modifier to add to your initiative roll.") {
                    required = false
                }
            }
            subCommand("end", "Ends your turn.")
            subCommand("down", "Removes you from combat.")
            subCommand("list", "Lists everyone in combat.")
            subCommand("delay", "Delays your turn until after the given person's") {
                user("user", "User to delay your turn after.") {
                    required = true
                    autocomplete = true
                }
            }
            subCommand("remind", "Reminds you in the given amount of turns of something! (0 for your next turn)") {
                integer("rounds", "In how many rounds to remind you. 0 reminds you in your next turn.") {
                    required = true
                }
                string("note", "What to remind you of.") {
                    required = true
                }
            }
            subCommand("reminders", "Lists all reminders for this combat.")
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent)
    {
        val channel = event.interaction.getChannel()
        val user = event.interaction.user

        val combat = trackedCombats.getOrPut(channel) { Combat() }
        val subCommand = event.interaction.command.data.options.value?.map { it.name } ?: emptyList()

        when (subCommand.firstOrNull())
        {
            "start" ->
            {
                val response = event.interaction.deferPublicResponse()

                if (combat.isRunning)
                {
                    response.respond {
                        content = "Combat has already started!"
                    }
                    return
                }

                val userToGo = combat.startCombat()

                userToGo?.let {
                    response.respond {
                        content = "Combat started! It is ${it.user.mention}'s turn!"
                    }
                } ?: response.respond {
                    content = "There are not enough people in combat! You need at least two!"
                }
            }

            "finish" ->
            {
                val response = event.interaction.deferPublicResponse()

                if (!combat.isRunning)
                {
                    response.respond {
                        content = "No combat has started yet!"
                    }
                    return
                }

                val winners = combat.finishCombat()

                if (winners.isEmpty())
                {
                    response.respond { content = "In war, there are no winners. (Just like in this combat, what the fuck did you do?)" }
                    return
                }

                response.respond {
                    content = "Combat over! Winners are: ${winners.joinToString(", ") { winner -> winner.user.mention }}"
                }
            }

            "leave" ->
            {
                val response = event.interaction.deferPublicResponse()

                val removed = combat.initiativeOrder.removeIf { it.user == user }

                if (removed)
                {
                    response.respond {
                        content = "${user.mention} left the combat!"
                    }
                    return
                }

                response.respond {
                    content = "You are not in combat!"
                }
            }

            "init" ->
            {
                val response = event.interaction.deferPublicResponse()

                if (combat.isRunning)
                {
                    response.respond {
                        content = "Combat is already running!"
                    }
                    return
                }

                if (combat.initiativeOrder.map { it.user }.contains(user))
                {
                    response.respond {
                        content = "You are already in combat!"
                    }
                    return
                }

                val result = event.interaction.command.integers["result"]?.toInt()
                val rollString = event.interaction.command.strings["roll"]

                if (result == null || rollString == null)
                {
                    response.respond {
                        content = "Something went wrong! Contact a moderator."
                    }
                    return
                }

                val rollResult = RollResult(rollString, 20, 1, 4)

                combat.setInitiative(user, result, rollResult)

                response.respond {
                    content = "Initiative set to $result for ${user.mention}!"
                }
            }

            "initiative" ->
            {
                val response = event.interaction.deferPublicResponse()

                if (combat.isRunning)
                {
                    response.respond {
                        content = "Combat is already running!"
                    }
                    return
                }

                if (combat.initiativeOrder.map { it.user }.contains(user))
                {
                    response.respond {
                        content = "You are already in combat!"
                    }
                    return
                }

                val amount = event.interaction.command.integers["amount"]?.toInt() ?: 1
                val modifier = event.interaction.command.integers["modifier"]?.toInt() ?: 4

                val rolls = RollResult(20, amount, modifier)

                combat.rollInitiative(user, rolls)

                val sb = StringBuilder()
                sb.append("${user.mention} rolled a ")
                sb.append(rolls.getRollString())
                sb.append(" for initiative!")

                response.respond {
                    content = sb.toString()
                }
            }

            "end" ->
            {
                val response = event.interaction.deferPublicResponse()

                if (!combat.isRunning)
                {
                    response.respond {
                        content = "Combat hasn't started yet!"
                    }
                    return
                }

                if (combat.combatantToGo!!.user != user)
                {
                    response.respond {
                        content = "Not your turn! It's ${combat.combatantToGo!!.user.mention}'s turn!"
                    }
                    return
                }

                val responseSb = StringBuilder()

                if (combat.nextCombatant()) responseSb.append("Round ${combat.roundTracker}! ")
                responseSb.append("${combat.combatantToGo!!.user.mention}'s turn!")

                val followup = response.respond {
                    content = responseSb.toString()
                }

                combat.checkForReminders()?.let {
                    followup.createPublicFollowup {
                        content = "Reminders:\n$it"
                    }
                }
            }

            "down" ->
            {
                val response = event.interaction.deferPublicResponse()

                if (!combat.isRunning)
                {
                    response.respond {
                        content = "Combat hasn't started yet!"
                    }
                    return
                }

                if (combat.removeFromCombat(user))
                {
                    response.respond {
                        content = "${user.mention} got downed!"
                    }
                    return
                }

                response.respond {
                    content = "You are not in combat!"
                }
            }

            "list" ->
            {
                val response = event.interaction.deferPublicResponse()

                val list = if (combat.isRunning) combat.listCombatants() else combat.listParticipants()

                response.respond {
                    content = "Current participants:\n$list"
                }
            }

            "delay" ->
            {
                val response = event.interaction.deferPublicResponse()

                val targetUser = event.interaction.command.users["user"]!!

                if (!combat.delayAfter(targetUser))
                {
                    response.respond {
                        content = "Delay unsuccessful! To delay, combat needs to be started and you have to delay after someone lower in initiative than you!"
                    }
                    return
                }

                response.respond {
                    content = "${user.mention} now takes their turn after ${targetUser.mention}!\nIt is ${combat.combatantToGo!!.user.mention}'s turn!"
                }
            }

            "remind" ->
            {
                val response = event.interaction.deferEphemeralResponse()

                if (!combat.isRunning)
                {
                    response.respond {
                        content = "Combat hasn't started yet!"
                    }
                    return
                }

                val roundAmount = event.interaction.command.integers["rounds"]!!.toInt()
                val note = event.interaction.command.strings["note"]!!

                combat.addReminder(roundAmount, user, note)

                response.respond {
                    content = "Reminding you of '$note' in round ${roundAmount + combat.roundTracker}!"
                }
            }

            "reminders" ->
            {
                val response = event.interaction.deferEphemeralResponse()

                if (!combat.isRunning)
                {
                    response.respond {
                        content = "Combat hasn't started yet!"
                    }
                    return
                }

                val reminders = combat.getAllActiveRemindersForUser(user)

                val sb = StringBuilder()

                for (reminder in reminders)
                {
                    sb.append("Round ${reminder.key}, Turn ${reminder.value.first}: ${reminder.value.second}\n")
                }

                response.respond {
                    content = sb.toString()
                }
            }
        }
    }

}

/**
 * @param trackedReminders Map of round number to a map of turn number to a pair of user and note.
 */
class Combat(val combatOrder: MutableList<Combatant?> = mutableListOf(), val trackedReminders: MutableMap<Int, MutableMap<Int, MutableList<Pair<User, String>>>> = mutableMapOf())
{
    var isRunning = false
    var turnTracker = 0
    var roundTracker = 0
    var combatantToGo: Combatant? = null
    var initiativeOrder: MutableList<Combatant> = mutableListOf()

    /**
     * Adds a reminder to this combat for the specified user.
     * @param roundDelay If 0, reminds the user on their next turn, otherwise in roundDelay rounds.
     */
    fun addReminder(roundDelay: Int, user: User, note: String)
    {
        val userTurn = combatOrder.map { it?.user }.indexOf(user)

        if (roundDelay > 0)
        {
            trackedReminders.getOrPut(roundDelay + roundTracker) { mutableMapOf() }.getOrPut(turnTracker) { mutableListOf() }.add(Pair(user, note))
            return
        }

        if (userTurn > turnTracker) trackedReminders.getOrPut(roundTracker) { mutableMapOf() }.getOrPut(userTurn) { mutableListOf() }.add(Pair(user, note))
        else trackedReminders.getOrPut(roundTracker + 1) { mutableMapOf() }.getOrPut(userTurn) { mutableListOf() }.add(Pair(user, note))
    }

    /**
     * Returns all notes of this user for this round at once.
     */
    fun checkForReminders(): String?
    {
        val notes = trackedReminders[roundTracker]?.get(turnTracker) ?: return null

        val sb = StringBuilder()

        for (note in notes)
        {
            sb.append("${note.first.mention}: ${note.second}\n")
        }

        return sb.toString()
    }

    fun getAllActiveRemindersForUser(user: User): Map<Int, Pair<Int, String>>
    {
        val reminders = mutableMapOf<Int, Pair<Int, String>>()

        for ((round, turnMap) in trackedReminders)
        {
            for ((turn, notes) in turnMap)
            {
                for (note in notes)
                {
                    if (((round == roundTracker && turn > turnTracker) || (round > roundTracker)) && note.first == user) reminders[round] = Pair(turn, note.second)
                }
            }
        }

        return reminders
    }

    fun resetReminders() = trackedReminders.clear()

    fun rollInitiative(user: User, rollResult: RollResult)
    {
        if (!rollResult.roll()) throw IllegalStateException("User ${user.effectiveName} does not have dice to roll!")

        initiativeOrder.add(Combatant(user, rollResult, alive = true))
    }

    /**
     * Returns true when user was found and init was set, false otherwise.
     */
    fun setInitiative(user: User, value: Int, rollResult: RollResult): Boolean
    {
        rollResult.result = value

        initiativeOrder.find { it.user == user }?.let {
            it.rollResult.result = value
            it.rollResult.diceType = rollResult.diceType
            it.rollResult.amount = rollResult.amount
            it.rollResult.modifier = rollResult.modifier
            return true
        }

        initiativeOrder.add(Combatant(user, rollResult, alive = true))

        return false
    }

    fun prepareList()
    {
        val groupedOrder = initiativeOrder.groupBy { it.rollResult.result }.toList().sortedByDescending { it.first }.toMutableList()
        while (groupedOrder.isNotEmpty())
        {
            val (_, currentCombatants) = groupedOrder.removeFirst()
            if (currentCombatants.size == 1) combatOrder.add(currentCombatants.first())
            else combatOrder.addAll(solveCombatantOrder(currentCombatants))
        }
    }

    fun startCombat(): Combatant?
    {
        resetReminders()
        prepareList()
        if (combatOrder.size <= 1) return null
        isRunning = true
        combatantToGo = combatOrder.first()
        return combatantToGo
    }

    fun finishCombat(): List<Combatant>
    {
        isRunning = false
        val winners = combatOrder.toList()
        combatOrder.clear()
        initiativeOrder.clear()
        roundTracker = 0
        return winners.filterNotNull()
    }

    /**
     * Returns true, if delay was successful, false otherwise.
     */
    fun delayAfter(targetUser: User): Boolean
    {
        combatantToGo?.let { ctg ->
            val ownIndex = combatOrder.map { it?.user }.indexOf(ctg.user)
            val indexToInsertAfter = combatOrder.map { it?.user }.indexOf(targetUser)

            val targetCombatant = combatOrder.firstOrNull { it?.user == targetUser && it.alive }

            if (ownIndex == -1 || indexToInsertAfter == -1) return false
            if (ownIndex >= indexToInsertAfter) return false
            if (targetCombatant == null) return false

            val originalIndex = combatOrder.indexOf(ctg)
            combatOrder[originalIndex] = null
            combatOrder.add(indexToInsertAfter + 1, ctg)
            nextCombatant()
            return true
        }
        return false
    }

    /**
     * Returns true, if user was found and removed from combat, false otherwise.
     */
    fun removeFromCombat(user: User): Boolean
    {
        val matches = combatOrder.filterNotNull().filter { it.user == user && it.alive }

        if (matches.isEmpty()) return false

        matches.forEach { it.alive = false }

        return true
    }

    /**
     * Returns true, if this is the last turn in the round.
     */
    fun isLastTurnInRound(): Boolean
    {
        if (turnTracker == combatOrder.size - 1) return true

        for (i in turnTracker + 1 until combatOrder.size)
        {
            val nextCombatant = combatOrder[i]
            if (nextCombatant != null && nextCombatant.alive) return false
        }

        return true
    }

    /**
     * Returns true, if round changed.
     */
    fun nextCombatant(): Boolean
    {
        var isNextRound = false

        if (isLastTurnInRound())
        {
            val remindersToPush = trackedReminders[roundTracker]?.filterKeys { it > turnTracker } ?: emptyMap()

            for (reminder in remindersToPush)
            {
                trackedReminders[roundTracker]?.remove(reminder.key)
                trackedReminders[roundTracker + 1]?.getOrPut(0) { mutableListOf() }?.addAll(reminder.value)
            }

            roundTracker++
            turnTracker = 0
            isNextRound = true
        }

        // While the cursor is on null or dead, accumulate reminders on next position.
        while (combatOrder[turnTracker]?.let { !it.alive } ?: true)
        {
            trackedReminders[roundTracker]?.remove(turnTracker)?.let {
                trackedReminders[roundTracker]?.getOrPut(turnTracker) { mutableListOf() }?.addAll(it)
            }
            turnTracker++
        }

        combatantToGo = combatOrder[turnTracker]
        return isNextRound
    }

    fun listParticipants(): String
    {
        val sb = StringBuilder()

        sb.append("Ties will be resolved after combat start!".italic()).append("\n")

        for (combatant in initiativeOrder.sortedByDescending { it.rollResult.result })
        {
            sb.append("${combatant.user.effectiveName} (${combatant.rollResult.result})\n")
        }

        return sb.toString()
    }

    fun listCombatants(): String
    {
        val sb = StringBuilder()

        for (combatant in combatOrder.filterNotNull())
        {
            sb.append("${combatant.user.effectiveName} (${combatant.rollResult.result})".let { if (combatant.alive) it else it.stricken() }).append("\n")
        }

        return sb.toString()
    }

    /**
     * Expects a list of combatants with the SAME trackedInitiative.
     */
    private fun solveCombatantOrder(combatants: List<Combatant>): List<Combatant>
    {
        val newCombatants = mutableListOf<Combatant>()

        for (combatant in combatants)
        {
            if (!combatant.rollResult.roll()) throw IllegalStateException("Combatant ${combatant.user.effectiveName} does not have dice to roll!")

            newCombatants.add(combatant)
        }

        val groupedCombatants = newCombatants.groupBy { it.rollResult.result }.toList().sortedByDescending { it.first }
        val finalCombatants = mutableListOf<Combatant>()

        for (group in groupedCombatants)
        {
            if (group.second.size == 1) finalCombatants.add(group.second.first())
            else finalCombatants.addAll(solveCombatantOrder(group.second))
        }

        return finalCombatants
    }
}

data class Combatant(val user: User, val rollResult: RollResult, var alive: Boolean)