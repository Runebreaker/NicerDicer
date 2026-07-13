package de.nicerdicer.util

import de.nicerdicer.util.StringFormatter.formatModifier
import kotlin.math.absoluteValue

class RollResult(var diceType: Int, var amount: Int, var modifier: Int)
{
    private val diceRolls: MutableList<Int> = mutableListOf()
    var result: Int = 0
    private var isCrit: Boolean = false

    constructor(
        rollString: String,
        defaultType: Int,
        defaultAmount: Int,
        defaultModifier: Int,
        parseLegacyShorthand: Boolean = false,
    ) : this(0, 0, 0)
    {
        this.amount = Regex("\\d+[dD]").find(rollString)?.value?.dropLast(1)?.toInt()
            ?: (if (parseLegacyShorthand) Regex("^\\d+(?=[+-])|^\\d+$").find(rollString)?.value?.toInt() else null)
            ?: defaultAmount
        this.diceType = Regex("[dD]\\d+").find(rollString)?.value?.drop(1)?.toInt() ?: defaultType
        this.modifier = if (parseLegacyShorthand)
        {
            Regex("([+-]{1,2})(\\d+)$").find(rollString)?.let {
                val signs = it.groupValues[1]
                val value = it.groupValues[2].toInt()
                when (signs)
                {
                    "++" -> defaultModifier + value
                    "--" -> defaultModifier - value
                    else -> "$signs$value".toInt()
                }
            } ?: defaultModifier
        } else
        {
            Regex("[+-]\\d+").find(rollString)?.value?.toInt() ?: defaultModifier
        }
    }

    /**
     * Rolls the dice new and sets the result and isCrit. Returns true, if successful or false otherwise.
     */
    fun roll(): Boolean
    {
        if (diceType <= 0 || amount <= 0) return false

        isCrit = false
        diceRolls.clear()
        repeat(amount)
        {
            val roll = NicerRandom.nextRoll(diceType)
            diceRolls.add(roll)
            if (roll == diceType) isCrit = true
        }
        result = diceRolls.max() + modifier

        return true
    }

    fun getDiceRolls(): List<Int> = diceRolls.toList()

    fun isCrit() = isCrit

    fun getRollString(): String = "${getInputString()} => ${toString()}"

    fun getInputString(): String = "$amount D$diceType ${formatModifier(modifier)}"

    override fun toString(): String
    {
        val sb = StringBuilder()

        sb.append(StringFormatter.formatRolls(diceRolls))
        sb.append(" (${formatModifier(modifier)}) = ")
        sb.append(StringFormatter.formatResult(result, isCrit))

        return sb.toString()
    }
}
