package com.glennwalker.bear.core

/**
 * The care menu behind the bottom button: play fetch and the food choices.
 * Shared by the app and the web preview so both offer the same things.
 */
object CareMenu {
    enum class Item(val food: FoodKind? = null) {
        FETCH, BERRY(FoodKind.BERRY), APPLE(FoodKind.APPLE), FISH(FoodKind.FISH), HONEY(FoodKind.HONEY),
    }

    val items = Item.entries

    fun icon(item: Item): List<String> = item.food?.let { Sprites.food(it) } ?: Sprites.BALL

    fun label(item: Item, sim: BearSim): String = when {
        item == Item.FETCH -> if (sim.fetchOut) "Put the ball away" else "Play fetch"
        else -> item.food!!.label
    }

    /** One line under the label saying what choosing it does, or why it can't be chosen now. */
    fun hint(item: Item, sim: BearSim): String {
        if (item == Item.FETCH) {
            return when {
                sim.fetchInPlay -> "Wait for the ball to come back"
                sim.fetchOut -> "Back in the toy box"
                else -> "Swipe the ball up to throw it"
            }
        }
        if (sim.food != null) return "Still has food to finish"
        return when (item.food!!) {
            FoodKind.BERRY -> "A light snack"
            FoodKind.APPLE -> "Crunchy and good for health"
            FoodKind.FISH -> "A big meal, restores energy"
            FoodKind.HONEY -> "A sweet treat, lifts the mood"
        }
    }

    fun enabled(item: Item, sim: BearSim): Boolean =
        if (item == Item.FETCH) !sim.fetchInPlay else sim.food == null

    fun choose(item: Item, sim: BearSim) {
        if (!enabled(item, sim)) return
        val food = item.food
        when {
            food != null -> sim.feed(food)
            sim.fetchOut -> sim.stopFetch()
            else -> sim.startFetch()
        }
    }
}
