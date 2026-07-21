package de.nicerdicer.util

import de.nicerdicer.functions.WoundFunction

object InteractableWoundsUtil
{
    val interactableWounds = mutableMapOf<String, (WoundLocation) -> List<WoundEffect>>()

    init
    {
        // Demolished
        interactableWounds["Demolished"] = {
            WoundFunction.wounds.getDemolished(it)
        }

        // Scalded
        interactableWounds["Scalded"] = {
            val effectsToReturn = mutableListOf<WoundEffect>()

            effectsToReturn.addAll(WoundFunction.wounds.roll(0, 0, 1, WoundType.BURN))
            effectsToReturn.addAll(WoundFunction.wounds.roll(0, 1, 0, WoundType.REND))

            effectsToReturn
        }

        // Freezer Burn
        interactableWounds["Freezer Burn"] = {
            val effectsToReturn = mutableListOf<WoundEffect>()

            effectsToReturn.addAll(WoundFunction.wounds.roll(0, 1, 0, WoundType.BURN))
            effectsToReturn.addAll(WoundFunction.wounds.roll(0, 1, 0, WoundType.REND))

            effectsToReturn
        }

        // Thin Ice
        interactableWounds["Thin Ice"] = {
            WoundFunction.wounds.roll(0, 0, 2, WoundType.FREEZE)
        }

        // Scattered
        interactableWounds["Scattered"] = {
            WoundFunction.wounds.roll(0, 0, 1, WoundType.SHOCK)
        }

        // Devastated
        interactableWounds["Devastated"] = {
            try
            {
                val rolledEffect = WoundFunction.wounds.rollDevastated()
                listOf(rolledEffect)
            }
            catch (e: Exception)
            {
                println("InteractableWoundsUtil: Error occurred while rolling Devastated wound.")
                e.printStackTrace()
                listOf()
            }
        }
    }
}