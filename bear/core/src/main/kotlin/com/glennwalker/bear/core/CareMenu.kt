package com.glennwalker.bear.core

/**
 * The care menu behind the bottom button: play fetch and the food choices.
 * Shared by the app and the web preview so both offer the same things.
 */
object CareMenu {
    enum class Item(val food: FoodKind? = null) {
        TALK, FETCH, BERRY(FoodKind.BERRY), APPLE(FoodKind.APPLE), FISH(FoodKind.FISH), HONEY(FoodKind.HONEY),
    }

    val items = Item.entries

    fun icon(item: Item): List<String> = when {
        item == Item.TALK -> Sprites.TALK
        item.food != null -> Sprites.food(item.food)
        else -> Sprites.BALL
    }

    fun label(item: Item, sim: BearSim): String = when {
        item == Item.TALK -> "Talk"
        item == Item.FETCH -> "Play fetch"
        else -> item.food!!.label
    }

    /** One line under the label saying what choosing it does, or why it can't be chosen now. */
    fun hint(item: Item, sim: BearSim): String {
        if (item == Item.TALK) return if (sim.mode == Mode.SLEEP) "Bear is napping, but will listen" else "Have a chat with Bear"
        if (item == Item.FETCH) return "Swipe the ball up to throw it"
        if (sim.food != null) return "Still has food to finish"
        return when (item.food!!) {
            FoodKind.BERRY -> "A light snack"
            FoodKind.APPLE -> "Crunchy and good for health"
            FoodKind.FISH -> "A big meal, restores energy"
            FoodKind.HONEY -> "A sweet treat, lifts the mood"
        }
    }

    /** The line under the screen while playing fetch. */
    fun fetchStatus(sim: BearSim): String = when {
        sim.throws == 0 -> "Swipe the ball up to throw it"
        else -> "${sim.throws} ${if (sim.throws == 1) "throw" else "throws"} · ${sim.fetched} brought back"
    }

    fun enabled(item: Item, sim: BearSim): Boolean = when (item) {
        Item.TALK -> true
        Item.FETCH -> true
        else -> sim.food == null
    }

    fun choose(item: Item, sim: BearSim) {
        if (!enabled(item, sim)) return
        val food = item.food
        when {
            item == Item.TALK -> sim.startActivity(Activity.TALK)
            food != null -> sim.feed(food)
            else -> sim.startActivity(Activity.FETCH)
        }
    }
}
