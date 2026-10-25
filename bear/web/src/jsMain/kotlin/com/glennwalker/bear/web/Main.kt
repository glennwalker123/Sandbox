package com.glennwalker.bear.web

import com.glennwalker.bear.core.Age
import com.glennwalker.bear.core.BearGrid
import com.glennwalker.bear.core.BearRaster
import com.glennwalker.bear.core.BearSim
import com.glennwalker.bear.core.CharacterSheet
import com.glennwalker.bear.core.Floor
import com.glennwalker.bear.core.SceneRaster
import com.glennwalker.bear.core.Tuning
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.PI
import kotlin.math.roundToInt

private const val LIT = "#DCE4C6"
private const val DIM = "#7D846F"
private const val OFF = "#1C1F18"
private const val BACKGROUND = "#0B0C09"

/** Dot states for [Leds.draw]. */
private const val DOT_OFF = 0
private const val DOT_LIT = 1
private const val DOT_DIM = 2

/**
 * A canvas of round LEDs: 6 px apart with a 2.4 px radius, as in the prototype.
 * Unlit dots are drawn once; each frame draws only lit and dim dots on top.
 */
private class Leds(val canvas: HTMLCanvasElement, val cols: Int, val rows: Int, val round: Boolean = false) {
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
        u.fillStyle = BACKGROUND
        u.fillRect(0.0, 0.0, unlit.width.toDouble(), unlit.height.toDouble())
        u.fillStyle = OFF
        u.beginPath()
        for (r in 0 until rows) for (c in 0 until cols) if (shown(c, r)) dot(u, c, r)
        u.fill()
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
        ctx.drawImage(unlit, 0.0, 0.0)
        for ((color, wanted) in listOf(LIT to DOT_LIT, DIM to DOT_DIM)) {
            ctx.fillStyle = color
            ctx.beginPath()
            for (r in 0 until rows) for (c in 0 until cols) if (shown(c, r) && state(c, r) == wanted) dot(ctx, c, r)
            ctx.fill()
        }
    }
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
    byId<HTMLButtonElement>("feed").onclick = { sim.feed(); drawScene() }
    byId<HTMLButtonElement>("worry").onclick = { sim.makeWorry(); drawScene() }
    ageButton.onclick = {
        sim.toggleAge()
        ageButton.textContent = if (sim.age == Age.CUB) "Show adult" else "Show cub"
        drawScene()
    }
    byId<HTMLCanvasElement>("glyph").onclick = { sim.pat(); drawScene() }

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
    val sceneView = byId<HTMLElement>("view-scene")
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
        if (steps > 0) { if (showingSheet) drawSheet() else drawScene() }
        window.requestAnimationFrame(::frame)
    }
    window.requestAnimationFrame(::frame)
}
