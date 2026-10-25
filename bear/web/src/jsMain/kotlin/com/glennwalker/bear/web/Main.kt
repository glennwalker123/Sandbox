package com.glennwalker.bear.web

import com.glennwalker.bear.core.Age
import com.glennwalker.bear.core.BearGrid
import com.glennwalker.bear.core.BearRaster
import com.glennwalker.bear.core.BearSim
import com.glennwalker.bear.core.BearTalk
import com.glennwalker.bear.core.CharacterSheet
import com.glennwalker.bear.core.CareMenu
import com.glennwalker.bear.core.Floor
import com.glennwalker.bear.core.SceneRaster
import com.glennwalker.bear.core.Sprites
import com.glennwalker.bear.core.Tuning
import com.glennwalker.bear.core.hear
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.roundToInt

private const val LIT = "#DCE4C6"
private const val DIM = "#7D846F"
private const val OFF = "#1C1F18"
private const val BACKGROUND = "#0B0C09"

/** Dot states for [Leds.draw]. */
private const val DOT_OFF = 0
private const val DOT_LIT = 1
private const val DOT_DIM = 2

/** Care menu: how long to hold before the menu opens, how far options sit from the button, and how close a finger must be to pick one. */
private const val HOLD_MS = 280
private const val MENU_RADIUS = 128.0
private const val HIT_RADIUS = 46.0

/**
 * A canvas of round LEDs: 6 px apart with a 2.4 px radius, as in the prototype.
 * Unlit dots are drawn once; each frame draws only lit and dim dots on top.
 */
private class Leds(
    val canvas: HTMLCanvasElement,
    val cols: Int,
    val rows: Int,
    val round: Boolean = false,
    /** Icons sit on a control's own colour, so they skip the black backing and unlit dots. */
    val backing: Boolean = true,
) {
    private val pitch = 6.0
    private val radius = 2.4
    private val ctx = canvas.context()
    private val unlit = document.createElement("canvas") as HTMLCanvasElement

    init {
        canvas.width = (cols * pitch).toInt()
        canvas.height = (rows * pitch).toInt()
        unlit.width = canvas.width
        unlit.height = canvas.height
        val u = unlit.context()
        if (backing) {
            u.fillStyle = BACKGROUND
            u.fillRect(0.0, 0.0, unlit.width.toDouble(), unlit.height.toDouble())
            u.fillStyle = OFF
            u.beginPath()
            for (r in 0 until rows) for (c in 0 until cols) if (shown(c, r)) dot(u, c, r)
            u.fill()
        }
    }

    /** The Glyph Matrix is a circle of LEDs inside the 25 × 25 grid. */
    private fun shown(c: Int, r: Int): Boolean {
        if (!round) return true
        val h = (cols - 1) / 2.0
        return (c - h) * (c - h) + (r - h) * (r - h) <= (h + 0.6) * (h + 0.6)
    }

    private fun dot(g: CanvasRenderingContext2D, c: Int, r: Int) {
        val x = c * pitch + pitch / 2
        val y = r * pitch + pitch / 2
        g.moveTo(x + radius, y)
        g.arc(x, y, radius, 0.0, 2 * PI)
    }

    fun draw(state: (col: Int, row: Int) -> Int) {
        ctx.clearRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
        ctx.drawImage(unlit, 0.0, 0.0)
        for ((color, wanted) in listOf(LIT to DOT_LIT, DIM to DOT_DIM)) {
            ctx.fillStyle = color
            ctx.beginPath()
            for (r in 0 until rows) for (c in 0 until cols) if (shown(c, r) && state(c, r) == wanted) dot(ctx, c, r)
            ctx.fill()
        }
    }
}

/** Draws a sprite ('#' lit, '+' dim) onto a matching grid of LEDs. */
private fun Leds.drawSprite(sprite: List<String>) = draw { x, y ->
    when (sprite.getOrNull(y)?.getOrNull(x)) {
        '#' -> DOT_LIT
        '+' -> DOT_DIM
        else -> DOT_OFF
    }
}

private fun buzz(ms: Int) {
    try { window.navigator.asDynamic().vibrate(ms) } catch (_: Throwable) {}
}

private fun HTMLCanvasElement.context() = getContext("2d") as CanvasRenderingContext2D

@Suppress("UNCHECKED_CAST")
private fun <T : HTMLElement> byId(id: String): T = document.getElementById(id) as T

private fun now(): Long = window.performance.now().toLong()

fun main() {
    val sim = BearSim()
    val raster = SceneRaster()

    val sceneCanvas = byId<HTMLCanvasElement>("scene")
    val scene = Leds(sceneCanvas, Floor.WIDTH, Floor.HEIGHT)
    val glyph = Leds(byId("glyph"), 25, 25, round = true)
    val status = byId<HTMLElement>("status")
    val ageButton = byId<HTMLButtonElement>("age")

    fun drawScene() {
        val dots = raster.render(sim)
        scene.draw { x, y ->
            val v = dots[y * Floor.WIDTH + x].toInt()
            when {
                v == SceneRaster.LIT -> DOT_LIT
                SceneRaster.isDim(v, x, y) -> DOT_DIM
                else -> DOT_OFF
            }
        }
        // The Glyph mirror shows the bear facing you, nudged up one row at the top of a hop.
        val bear = BearRaster.build(sim.look().copy(back = false), 25)
        val lift = if (sim.hopHeight >= 2) 1 else 0
        glyph.draw { x, y ->
            val sy = y + lift
            if (sy >= 25) DOT_OFF else when (bear[x, sy]) {
                BearGrid.FUR -> DOT_LIT
                BearGrid.INNER_EAR -> DOT_DIM
                else -> DOT_OFF
            }
        }
        status.textContent = "${sim.mode.name.lowercase()} · ${sim.look().mood.name.lowercase()} · " +
            "♥ ${sim.happiness.roundToInt()}  ⚡ ${sim.energy.roundToInt()}  ✚ ${sim.health.roundToInt()}"
    }

    // ---- Controls ----
    byId<HTMLButtonElement>("worry").onclick = { sim.makeWorry(); drawScene() }
    ageButton.onclick = {
        sim.toggleAge()
        ageButton.textContent = if (sim.age == Age.CUB) "Show adult" else "Show cub"
        drawScene()
    }
    byId<HTMLCanvasElement>("glyph").onclick = { sim.pat(); drawScene() }

    // ---- Conversation ----
    val speech = byId<HTMLElement>("speech")
    val said = byId<HTMLElement>("said")
    val bearLine = byId<HTMLElement>("bear-line")
    val chatBar = byId<HTMLElement>("chat")
    val careDock = byId<HTMLElement>("care-dock")
    val chips = byId<HTMLElement>("chips")
    val sayInput = byId<HTMLInputElement>("say")
    val sendButton = byId<HTMLButtonElement>("send")
    Leds(byId("send-icon"), 9, 9, backing = false).drawSprite(Sprites.SEND)

    val turns = mutableListOf<dynamic>()
    var lastSuggestions = BearTalk.OPENERS
    var busy = false
    var typing = -1
    var sample: dynamic = null
    window.asDynamic().claude?.use("sample")?.then { s: dynamic -> sample = s }

    fun showLine(text: String, note: Boolean = false) {
        window.clearInterval(typing)
        bearLine.classList.toggle("note", note)
        bearLine.classList.remove("thinking")
        if (note) { bearLine.textContent = text; return }
        // Type the words out, a few letters at a time, like a scrolling LED sign.
        var shown = 0
        bearLine.textContent = ""
        typing = window.setInterval({
            shown = minOf(text.length, shown + 2)
            bearLine.textContent = text.substring(0, shown)
            if (shown >= text.length) window.clearInterval(typing)
        }, 28)
    }

    fun showThinking() {
        window.clearInterval(typing)
        bearLine.classList.remove("note")
        bearLine.classList.add("thinking")
        bearLine.innerHTML = "<span>•</span><span>•</span><span>•</span>"
    }

    lateinit var send: (String) -> Unit
    fun showSuggestions(list: List<String>) {
        chips.innerHTML = ""
        for (text in list) {
            val chip = document.createElement("button") as HTMLButtonElement
            chip.type = "button"
            chip.textContent = text
            chip.disabled = busy
            chip.onclick = { send(text) }
            chips.appendChild(chip)
        }
    }

    fun setBusy(value: Boolean) {
        busy = value
        sendButton.disabled = value
        for (i in 0 until chips.children.length) (chips.children.item(i) as HTMLButtonElement).disabled = value
    }

    fun failureNote(code: String?): String? = when (code) {
        "cancelled" -> null
        "not_granted", "sampling_disabled", "not_declared", "capability_disabled", "capability_removed" ->
            "Bear can't talk here: Claude isn't allowed on this page."
        "rate_limited" -> "That's a lot of talking for now. Try again in a little while."
        "session_expired" -> "Sign in to Claude again to keep talking."
        "refused" -> "Bear didn't catch that. Try saying it another way."
        "invalid_json", "empty_completion" -> "Bear lost its words. Try again."
        else -> "Bear couldn't hear you just then. Try again."
    }

    send = send@{ raw ->
        val text = raw.trim()
        if (text.isEmpty() || busy) return@send
        val ask = sample
        said.textContent = text
        sayInput.value = ""
        if (ask == null) {
            showLine("Talking needs Claude, which isn't available in this view.", note = true)
            return@send
        }
        setBusy(true)
        showThinking()
        val userTurn = js("({})")
        userTurn.role = "user"
        userTurn.content = BearTalk.userTurn(sim, text)
        val instructions = js("({})")
        instructions.role = "user"
        instructions.content = BearTalk.instructions
        val input = js("[]")
        input.push(instructions)
        for (t in turns) input.push(t)
        input.push(userTurn)
        val options = js("({})")
        options.modelTier = "quick"
        options.cache = false
        (ask.json(input, options) as kotlin.js.Promise<dynamic>).then({ data: dynamic ->
            val list = data?.suggestions
            val suggestions = if (list != null && js("Array").isArray(list) as Boolean) {
                (0 until (list.length as Int)).map { i -> list[i].toString() }
            } else null
            val reply = BearTalk.reply(data?.say?.toString(), suggestions, data?.react?.toString())
            val bearTurn = js("({})")
            bearTurn.role = "assistant"
            bearTurn.content = JSON.stringify(data)
            turns.add(userTurn)
            turns.add(bearTurn)
            while (turns.size > BearTalk.MAX_TURNS) turns.removeAt(0)
            sim.hear(reply.reaction)
            buzz(8)
            setBusy(false)
            showLine(reply.say)
            lastSuggestions = reply.suggestions
            showSuggestions(reply.suggestions)
            drawScene()
        }, { e: dynamic ->
            setBusy(false)
            val note = failureNote(e?.code?.toString())
            if (note != null) showLine(note, note = true) else bearLine.textContent = ""
        })
    }

    fun openChat() {
        speech.hidden = false
        chatBar.hidden = false
        careDock.hidden = true
        said.textContent = ""
        showLine(BearTalk.greeting(sim))
        showSuggestions(lastSuggestions)
    }

    fun closeChat() {
        window.clearInterval(typing)
        speech.hidden = true
        chatBar.hidden = true
        careDock.hidden = false
    }

    byId<HTMLElement>("say-form").addEventListener("submit", { e ->
        e.preventDefault()
        send(sayInput.value)
    })
    byId<HTMLButtonElement>("chat-done").onclick = { closeChat() }
    sendButton.addEventListener("pointerdown", { _: Event -> if (!busy) sendButton.classList.add("down") })
    for (type in listOf("pointerup", "pointercancel", "pointerleave")) {
        sendButton.addEventListener(type, { _: Event -> sendButton.classList.remove("down") })
    }

    // ---- Care button and menu ----
    val sceneView = byId<HTMLElement>("view-scene")
    val care = byId<HTMLButtonElement>("care")
    val menu = byId<HTMLElement>("menu")
    val menuTitle = byId<HTMLElement>("menu-title")
    val menuHint = byId<HTMLElement>("menu-hint")
    Leds(byId("care-icon"), 9, 9, backing = false).drawSprite(Sprites.PAW)

    var open = false
    var holding = false
    var hot: CareMenu.Item? = null
    var holdTimer = -1
    val options = CareMenu.items.mapIndexed { i, item ->
        val option = document.createElement("button") as HTMLButtonElement
        option.className = "option"
        option.setAttribute("role", "menuitem")
        // Fan across the top of the button, left to right.
        val angle = (165.0 - i * 30.0) * PI / 180
        option.style.setProperty("--x", "${(cos(angle) * MENU_RADIUS).roundToInt()}px")
        option.style.setProperty("--y", "${(-sin(angle) * MENU_RADIUS).roundToInt()}px")
        option.style.transitionDelay = "${i * 22}ms"
        val icon = document.createElement("canvas") as HTMLCanvasElement
        option.appendChild(icon)
        Leds(icon, 9, 9, backing = false).drawSprite(CareMenu.icon(item))
        menu.appendChild(option)
        item to option
    }

    fun refreshMenu() {
        for ((item, option) in options) {
            option.disabled = !CareMenu.enabled(item, sim)
            option.classList.toggle("hot", item == hot)
            option.setAttribute("aria-label", "${CareMenu.label(item, sim)}. ${CareMenu.hint(item, sim)}")
        }
        val h = hot
        menuTitle.textContent = if (h != null) CareMenu.label(h, sim) else "Care"
        menuHint.textContent = when {
            h != null -> CareMenu.hint(h, sim)
            holding -> "Slide to an option and let go"
            else -> "Choose an option"
        }
    }

    fun setOpen(value: Boolean) {
        open = value
        if (!value) hot = null
        sceneView.classList.toggle("menu-open", value)
        care.setAttribute("aria-expanded", value.toString())
        refreshMenu()
    }

    fun choose(item: CareMenu.Item) {
        if (!CareMenu.enabled(item, sim)) return
        CareMenu.choose(item, sim)
        buzz(12)
        setOpen(false)
        if (item == CareMenu.Item.TALK) openChat()
        drawScene()
    }

    for ((item, option) in options) option.onclick = { if (!holding) choose(item) }

    care.addEventListener("pointerdown", { ev ->
        val e = ev as PointerEvent
        e.preventDefault()
        care.asDynamic().setPointerCapture(e.pointerId)
        care.classList.add("down")
        buzz(6)
        holding = false
        holdTimer = window.setTimeout({
            // Held: open (or keep open) and let the finger slide onto an option.
            holding = true
            if (!open) setOpen(true) else refreshMenu()
            buzz(14)
        }, HOLD_MS)
    })
    care.addEventListener("pointermove", { ev ->
        if (!holding) return@addEventListener
        val e = ev as PointerEvent
        var nearest: CareMenu.Item? = null
        var best = HIT_RADIUS
        for ((item, option) in options) {
            val r = option.getBoundingClientRect()
            val d = hypot(e.clientX - (r.left + r.width / 2), e.clientY - (r.top + r.height / 2))
            if (d < best) { best = d; nearest = item }
        }
        if (nearest != hot) {
            hot = nearest
            if (nearest != null) buzz(5)
            refreshMenu()
        }
    })
    care.addEventListener("pointerup", { _: Event ->
        window.clearTimeout(holdTimer)
        care.classList.remove("down")
        if (holding) {
            holding = false
            val h = hot
            if (h != null && CareMenu.enabled(h, sim)) choose(h) else { hot = null; refreshMenu() }
        } else {
            setOpen(!open)
        }
    })
    care.addEventListener("pointercancel", { _: Event ->
        window.clearTimeout(holdTimer)
        care.classList.remove("down")
        holding = false
    })
    // Keyboard: Enter or Space toggles the menu (pointer clicks are handled above).
    care.addEventListener("click", { ev -> if (ev.asDynamic().detail == 0) setOpen(!open) })
    byId<HTMLElement>("scrim").onclick = { setOpen(false) }
    document.addEventListener("keydown", { ev -> if (ev.asDynamic().key == "Escape" && open) setOpen(false) })

    // ---- Touch on the scene, in scene dots ----
    fun scenePoint(e: PointerEvent): Pair<Double, Double> {
        val r = sceneCanvas.getBoundingClientRect()
        return (e.clientX - r.left) / r.width * Floor.WIDTH to (e.clientY - r.top) / r.height * Floor.HEIGHT
    }
    sceneCanvas.addEventListener("pointerdown", { ev ->
        val e = ev as PointerEvent
        sceneCanvas.asDynamic().setPointerCapture(e.pointerId)
        val (x, y) = scenePoint(e)
        sim.pointerDown(x, y, now())
    })
    sceneCanvas.addEventListener("pointermove", { ev ->
        val (x, y) = scenePoint(ev as PointerEvent)
        sim.pointerMove(x, y, now())
    })
    sceneCanvas.addEventListener("pointerup", { ev ->
        val (x, y) = scenePoint(ev as PointerEvent)
        sim.pointerUp(x, y, now())
        drawScene()
    })
    sceneCanvas.addEventListener("pointercancel", { _: Event -> sim.pointerCancel() })

    // ---- Character sheet ----
    val sheet = byId<HTMLElement>("view-sheet")
    val sheetBears = mutableListOf<Triple<Leds, CharacterSheet.Entry, Pair<Age, Int>>>()
    for (age in Age.entries) {
        val heading = document.createElement("h2")
        heading.textContent = if (age == Age.CUB) "Cub" else "Adult"
        sheet.appendChild(heading)
        for (entry in CharacterSheet.entries) {
            val row = document.createElement("div").apply { className = "row" }
            row.appendChild(document.createElement("div").apply { className = "label"; textContent = entry.label })
            val bears = document.createElement("div").apply { className = "bears" }
            for (n in CharacterSheet.sizes) {
                val cell = document.createElement("div").apply { className = "bear" }
                val canvas = document.createElement("canvas") as HTMLCanvasElement
                // Same dot spacing at every size, so sizes compare directly: 90 dots across ≈ 330 px.
                canvas.style.width = "${n * 3.6}px"
                canvas.style.height = "${n * 3.6}px"
                cell.appendChild(canvas)
                cell.appendChild(document.createElement("span").apply { textContent = "N = $n" })
                bears.appendChild(cell)
                sheetBears += Triple(Leds(canvas, n, n), entry, age to n)
            }
            row.appendChild(bears)
            sheet.appendChild(row)
        }
    }
    var sheetTick = 0
    fun drawSheet() {
        for ((leds, entry, key) in sheetBears) {
            val (age, n) = key
            val bear = BearRaster.build(entry.look(age, sheetTick), n)
            val lift = CharacterSheet.lift(entry, sheetTick, n)
            leds.draw { x, y ->
                val sy = y + lift
                if (sy >= n) DOT_OFF else when (bear[x, sy]) {
                    BearGrid.FUR -> DOT_LIT
                    BearGrid.INNER_EAR -> DOT_DIM
                    else -> DOT_OFF
                }
            }
        }
    }

    // ---- Views ----
    val sceneTab = byId<HTMLButtonElement>("tab-scene")
    val sheetTab = byId<HTMLButtonElement>("tab-sheet")
    var showingSheet = false
    fun show(sheetView: Boolean) {
        showingSheet = sheetView
        sceneView.hidden = sheetView
        sheet.hidden = !sheetView
        sceneTab.setAttribute("aria-pressed", (!sheetView).toString())
        sheetTab.setAttribute("aria-pressed", sheetView.toString())
        if (sheetView) drawSheet() else drawScene()
    }
    sceneTab.onclick = { show(false) }
    sheetTab.onclick = { show(true) }
    show(window.location.hash == "#sheet")

    // ---- Loop: fixed simulation steps, like the app ----
    var last = 0.0
    var pending = 0.0
    fun frame(time: Double) {
        if (last == 0.0) last = time
        pending = minOf(pending + time - last, 500.0)
        last = time
        var steps = 0
        while (pending >= Tuning.TICK_MS) {
            pending -= Tuning.TICK_MS
            steps++
            sim.step()
            if (showingSheet) sheetTick++
        }
        if (steps > 0) {
            if (showingSheet) drawSheet() else drawScene()
            if (open) refreshMenu()
        }
        window.requestAnimationFrame(::frame)
    }
    window.requestAnimationFrame(::frame)
}
