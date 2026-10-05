package io.oniimai.kanade

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/*
 * First-run setup motion in 2.5D: the Oniimai controller drawn from the front as flat layers at their real
 * depths (a stand-in plate until real product renders arrive). The built-in screen sits under the panel,
 * the acrylic frame, ring and keys stand out from it, and each step holds a slightly different angle, so
 * the layers show their thickness and shift against each other when the view moves. Thin lines, rings,
 * small waves and light point at what to do next.
 *
 * Nothing here decides that something worked. Every state that claims a result (plugged in, permission,
 * a button or touch area answering) comes from [SetupSignals], which the app fills from real hardware
 * state. Without it ([SetupSignals.live] false) the scenes only guide.
 */

/** Which part of setup the motion is showing. */
internal enum class MotionScene { INTRO, CONNECT, INPUT, LIGHTING, LANGUAGE, MONITOR, DONE }

/** The USB link as the app sees it. */
internal enum class LinkState { WAITING, PERMISSION, CONNECTED, PARTIAL, FAILED }

/** Which input the input-check step is looking at. */
internal enum class InputView { BUTTONS, TOUCH }

/**
 * What the app actually knows about the controller during setup. [live] is false when setup cannot see
 * the controller (first-run setup does not scan USB until it is finished); scenes then only guide.
 * [buttons] holds ring buttons 1-8 in bits 0-7, [touches] the 34 touch areas in Protocol.ZONES order.
 */
internal data class SetupSignals(
    val live: Boolean = false,
    val link: LinkState = LinkState.WAITING,
    val buttons: Int = 0,
    val touches: Long = 0L,
    val buttonsLinked: Boolean = false,
    val touchLinked: Boolean = false,
    val ledLinked: Boolean = false,
    /** A display is attached: the controller's built-in screen, as far as the app can tell. */
    val screenAttached: Boolean = false,
    /** The game is really being shown on it. Before setup is finished this stays false. */
    val screenShowing: Boolean = false,
)

/** Words drawn inside the scene, already translated by the caller. */
internal class MotionLabels(val usb: String, val keys: String, val aime: String, val screen: String, val ready: String)

/** Fast, then a long soft landing: the curve for entrances and moves. */
private val Settle = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
/** Highlights and fades. */
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val LINE = Color(0xFFC5CAD3)
private val MUTED = Color(0xFF8A8F99)
private val AMBER = Color(0xFFE8A33A)
private val LED_COLORS = listOf(Color(0xFFFF6FB5), Color(0xFFB07BFF), Color(0xFF5BC8FF))

/**
 * The setup motion for one [scene]. Keep one instance across steps (a stable list key) so the controller
 * stays put and only the guidance moves: framing and angle glide between steps in 600 ms (the layers
 * shift against each other on the way), overlays fade in 300 ms. [intro] plays the one-second entrance
 * (the body rises in, the frame, ring and keys settle onto it, a light runs round the keys).
 */
@Composable
internal fun SetupMotion(scene: MotionScene, signals: SetupSignals, labels: MotionLabels, screens: Boolean, leds: Boolean,
                         clockwise: Boolean, modifier: Modifier = Modifier, height: Dp = 340.dp,
                         inputView: InputView = InputView.BUTTONS, checkedButtons: Int = 0, checkedTouches: Long = 0L,
                         focusAime: Boolean = false, intro: Boolean = false, fadeEdges: Boolean = false, view: SetupView? = null) {
    val dark = isSystemInDarkTheme()
    val brand = MiuixTheme.colorScheme.primary
    val ink = MiuixTheme.colorScheme.onSurface
    // Room for the callouts and the controller screen's words, so none of them is laid out again every frame.
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val words = screenWords(view)
    val still = motionStill()
    val active = motionActive()
    // The clock lives outside snapshot state, so composition never re-runs per frame; only the drawing
    // reads [frames] and redraws.
    val clock = remember { LongArray(1) }
    var frames by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, still) {
        if (!active || still) return@LaunchedEffect
        val offset = withFrameNanos { it } - clock[0]
        while (true) withFrameNanos { clock[0] = it - offset; frames++ }
    }
    val sceneStart = remember(scene) { clock[0] }
    val linkStart = remember(signals.link, signals.live) { clock[0] }
    val viewStart = remember(view?.step) { clock[0] }
    val mirror = MirrorLayers(rememberGraphicsLayer(), rememberGraphicsLayer()).let { fresh -> remember { fresh } }
    val ms = if (still) 0 else 1
    val frame = MotionFraming.of(scene)
    val move = tween<Float>(600 * ms, easing = Settle)
    val fx by animateFloatAsState(frame.x, move); val fy by animateFloatAsState(frame.y, move)
    val zoom by animateFloatAsState(frame.zoom, move)
    val yaw by animateFloatAsState(frame.yaw, move); val pitch by animateFloatAsState(frame.pitch, move)
    @Composable fun fade(on: Boolean, duration: Int = 300) =
        animateFloatAsState(if (on) 1f else 0f, tween(duration * ms, easing = Standard)).value
    val phone = fade(scene == MotionScene.CONNECT, 400)
    val keysView = fade(scene == MotionScene.INPUT && inputView == InputView.BUTTONS)
    val touchView = fade(scene == MotionScene.INPUT && inputView == InputView.TOUCH)
    val ledView = fade(scene == MotionScene.LIGHTING && leds)
    val picture = fade((scene == MotionScene.MONITOR || scene == MotionScene.DONE) && screens, 400)
    val outline = fade(scene == MotionScene.MONITOR, 400)
    val aimeView = fade(scene == MotionScene.LIGHTING && focusAime)
    val introT = remember { Animatable(if (intro && !still) 0f else 1f) }
    LaunchedEffect(Unit) { if (introT.value < 1f) introT.animateTo(1f, tween(1100, easing = LinearEasing)) }
    // Choosing a rotation turns only the picture on the built-in screen a quarter turn until it stands upright.
    val turn = remember { Animatable(0f) }
    LaunchedEffect(scene, clockwise) {
        if (scene == MotionScene.MONITOR && !still) { turn.snapTo(if (clockwise) -90f else 90f); turn.animateTo(0f, tween(1100, delayMillis = 300, easing = Settle)) }
    }
    // Animations turned off while setup is open: finish the entrance and the turn where they would end.
    LaunchedEffect(still) { if (still) { introT.snapTo(1f); turn.snapTo(0f) } }
    // Real button edges: pressing fills the key at once, letting go sends one thin wave.
    val pressAt = remember { LongArray(8) }
    val releaseAt = remember { LongArray(8) { Long.MIN_VALUE } }
    val lastMask = remember { IntArray(1) }
    LaunchedEffect(signals.buttons, signals.live) {
        val mask = if (signals.live) signals.buttons else 0
        for (i in 0 until 8) {
            val was = lastMask[0] shr i and 1; val now = mask shr i and 1
            if (now == 1 && was == 0) pressAt[i] = clock[0]
            if (now == 0 && was == 1) releaseAt[i] = clock[0]
        }
        lastMask[0] = mask
    }
    Box(modifier.fillMaxWidth().height(height).semantics { contentDescription = "Oniimai" }) {
        // Close framings run past the stage; keep them inside it.
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            if (frames < 0 || size.width < 1f || size.height < 1f) return@Canvas
            val nanos = clock[0]
            val shot = MotionShot(nanos, nanos / 1e9f, dark, brand, ink, still, introT.value, phone, keysView, touchView, ledView, picture, outline,
                aimeView, turn.value, (nanos - sceneStart) / 1e9f, (nanos - linkStart) / 1e9f, scene, signals, checkedButtons, checkedTouches,
                pressAt, releaseAt, fadeEdges, view, (nanos - viewStart) / 1_000_000, (nanos - linkStart) / 1_000_000, words, measurer, mirror)
            drawSetup(shot, fx, fy, zoom, yaw, pitch, labels, measurer)
        }
    }
}

/**
 * Where the scene looks (model mm, y up), how close, and the small angle each step holds (degrees: yaw > 0
 * turns the left side, where the USB-C port is, towards the viewer; pitch > 0 looks down onto the top).
 */
private class MotionFraming(val x: Float, val y: Float, val zoom: Float, val yaw: Float, val pitch: Float) {
    companion object {
        fun of(scene: MotionScene) = when (scene) {
            MotionScene.INTRO -> MotionFraming(0f, -9f, 1f, -9f, 6f)
            MotionScene.LANGUAGE -> MotionFraming(0f, -9f, 1f, -7f, 5f)
            // Phone on the left, the controller turned to show its port side, the cable between.
            MotionScene.CONNECT -> MotionFraming(-80f, -12f, 0.93f, 16f, 4f)
            // In on the ring, looking a little down so the raised keys read as keys.
            MotionScene.INPUT -> MotionFraming(0f, -97f, 1.45f, 0f, 12f)
            MotionScene.LIGHTING -> MotionFraming(0f, -60f, 1.2f, -7f, 9f)
            // A slight turn shows the screen sitting under the panel's openings.
            MotionScene.MONITOR -> MotionFraming(0f, -9f, 1f, 7f, 5f)
            MotionScene.DONE -> MotionFraming(0f, -9f, 1f, -9f, 6f)
        }
    }
}

private class MotionShot(val nanos: Long, val t: Float, val dark: Boolean, val brand: Color, val ink: Color, val still: Boolean, val intro: Float,
                   val phone: Float, val keys: Float, val touch: Float, val led: Float, val picture: Float, val outline: Float, val aime: Float,
                   val turn: Float, val since: Float, val linkSince: Float, val scene: MotionScene, val signals: SetupSignals,
                   val checkedButtons: Int, val checkedTouches: Long, val pressAt: LongArray, val releaseAt: LongArray, val fadeEdges: Boolean,
                   /** What the controller's own screen shows (and how long into its step and link state), drawn on the model's screen too. */
                   val view: SetupView?, val viewMs: Long, val linkMs: Long, val words: ScreenWords, val measurer: TextMeasurer,
                   val mirror: MirrorLayers)

/**
 * Model millimetres (x right, y up, z towards the viewer) to canvas pixels, with the step's small turn and a
 * long lens. [lift] raises or lowers everything drawn through this view (the entrance rises from below).
 */
private class PlateView(val cx: Float, val cy: Float, val fx: Float, val fy: Float, val s: Float, yaw: Float, pitch: Float, val lift: Float = 0f) {
    private val cyw = cos(yaw * PI.toFloat() / 180f); private val syw = sin(yaw * PI.toFloat() / 180f)
    private val cp = cos(pitch * PI.toFloat() / 180f); private val sp = sin(pitch * PI.toFloat() / 180f)
    private val args = floatArrayOf(cx, cy, fx, fy, s, yaw, pitch)
    val yawSin = syw
    fun lifted(by: Float) = PlateView(cx, cy, fx, fy, s, args[5], args[6], lift + by)
    fun pt(x: Float, y: Float, z: Float = 0f): Offset {
        val dx = x - fx; val dy = y + lift - fy
        val x1 = dx * cyw + z * syw; val z1 = -dx * syw + z * cyw
        val y2 = dy * cp - z1 * sp; val z2 = dy * sp + z1 * cp
        val k = 1400f / (1400f - z2)
        return Offset(cx + x1 * k * s, cy - y2 * k * s)
    }
    fun pt(p: Offset, z: Float = 0f) = pt(p.x, p.y, z)
    fun projected(points: List<Offset>, z: Float) = points.map { pt(it, z) }
    fun path(points: List<Offset>, z: Float, into: Path = Path()) = into.apply {
        val a = pt(points[0], z); moveTo(a.x, a.y)
        for (i in 1 until points.size) { val b = pt(points[i], z); lineTo(b.x, b.y) }
        close()
    }
    fun circle(c: Offset, r: Float, z: Float, into: Path = Path(), n: Int = 48) = path(PlateGeo.circle(c, r, n), z, into)
}

/** Fixed geometry of the controller front (model mm), from the photos and the 41 x 28 cm body; z is depth. */
private object PlateGeo {
    const val W = 140f
    val SCREEN = Offset(0f, -97f)
    const val SCREEN_R = 76f
    const val RING_R = 117f
    val PORT = Offset(-W, 131f)
    const val PORT_Z = -17f
    val AIME = Offset(110f, 10f)
    const val MON_HW = 108f; const val MON_TOP = 160f; const val MON_BOTTOM = -224f
    val PHONE = Offset(-250f, -40f); const val PHONE_W = 84f; const val PHONE_H = 172f

    // Depths: the body is 34 mm deep with its face at 0; the built-in screen sits 6 mm under the face.
    const val BODY_BACK = -34f; const val SCREEN_Z = -6f
    const val FRAME_Z = 4f; const val RING_Z = 5f; const val KEY_Z = 12f; const val TRIM_Z = 3f
    const val PHONE_BACK = 6f; const val PHONE_FACE = 14f

    val body = roundRect(0f, -30f, 2 * W, 390f, 26f, 7)
    val frame = roundRect(0f, 106f, 2 * W, 132f, 22f, 6)
    val window = roundRect(0f, 106f, 210f, 90f, 7f, 4)
    // Ears as on the controller: about 3 cm above the frame, the speaker in the middle of the visible part.
    val ears = listOf(-1f, 1f).map { side ->
        val tri = listOf(Offset(side * W, 140f), Offset(side * 34f, 140f), Offset(side * 112f, 228f))
        roundPolygon(if (side < 0) tri else tri.reversed(), 26f, 8)
    }
    val speakers = listOf(Offset(-100.5f, 188f), Offset(100.5f, 188f))
    // Keycaps: 34 mm squares across the ring band, centred 22.5 degrees off each axis; key i is IO4 button i + 1.
    val keyCenters = (0 until 8).map { val a = (67.5f - 45f * it) * PI.toFloat() / 180f; Offset(SCREEN.x + 97f * cos(a), SCREEN.y + 97f * sin(a)) }
    val keys = (0 until 8).map { i ->
        val a = (67.5f - 45f * i) * PI.toFloat() / 180f
        val ux = cos(a); val uy = sin(a); val tx = -uy; val ty = ux; val c = keyCenters[i]; val h = 17f
        roundPolygon(listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (su, st) ->
            Offset(c.x + su * h * ux + st * h * tx, c.y + su * h * uy + st * h * ty) }, 6f, 4)
    }
    val start = roundPolygon(listOf(Offset(-121f, 2f), Offset(-96f, 14f), Offset(-121f, 26f)), 3f, 3)
    val ringOuter = circle(SCREEN, RING_R, 64)
    val ringInner = circle(SCREEN, SCREEN_R, 64)
    val aimeEdge = roundRect(AIME.x, AIME.y, 36f, 28f, 4f, 3)
    val aimeInner = roundRect(AIME.x, AIME.y, 28f, 20f, 3f, 3)
    val monitor = listOf(Offset(-MON_HW, MON_TOP), Offset(MON_HW, MON_TOP), Offset(MON_HW, MON_BOTTOM), Offset(-MON_HW, MON_BOTTOM))
    val display = roundRect(0f, (MON_TOP + MON_BOTTOM) / 2, 2 * MON_HW - 10f, MON_TOP - MON_BOTTOM - 10f, 3f, 3)
    // The USB-C port on the left side wall, as (y, z) on the plane x = -W.
    val portSlot = roundRect(PORT.y, PORT_Z, 13f, 5f, 2.4f, 3)
    val phoneBody = roundRect(PHONE.x, PHONE.y, PHONE_W, PHONE_H, 14f, 6)
    val phoneScreen = roundRect(PHONE.x, PHONE.y, PHONE_W - 10f, PHONE_H - 10f, 10f, 5)
    // The cable: out of the phone's bottom port, down, along, up between the devices and level into the port.
    val phonePlugTop = PHONE.y - PHONE_H / 2
    val phonePlug = roundRect(PHONE.x, phonePlugTop - 12f, 12f, 24f, 4f, 3)
    val portPlug = roundRect(PORT.x - 12f, PORT.y, 24f, 12f, 4f, 3)
    val route: List<Offset> = run {
        val r = 14f; val floor = -232f; val riser = -186f
        val start = Offset(PHONE.x, phonePlugTop - 24f); val end = Offset(PORT.x - 24f, PORT.y)
        val pts = mutableListOf(start)
        fun corner(c: Offset, to: Offset) {
            val a = pts.last()
            for (k in 1..8) { val u = k / 8f; val v = 1 - u
                pts += Offset(v * v * a.x + 2 * v * u * c.x + u * u * to.x, v * v * a.y + 2 * v * u * c.y + u * u * to.y) }
        }
        pts += Offset(start.x, floor + r); corner(Offset(start.x, floor), Offset(start.x + r, floor))
        pts += Offset(riser - r, floor); corner(Offset(riser, floor), Offset(riser, floor + r))
        pts += Offset(riser, end.y - r); corner(Offset(riser, end.y), Offset(riser + r, end.y))
        pts += end
        pts
    }
    val routeLength = FloatArray(route.size).also { for (i in 1 until route.size) it[i] = it[i - 1] + (route[i] - route[i - 1]).getDistance() }
    /** The cable leaves the phone at mid-thickness and reaches the port's depth on the side wall. */
    fun routeZ(f: Float) = (PHONE_BACK + PHONE_FACE) / 2 + (PORT_Z - (PHONE_BACK + PHONE_FACE) / 2) * f
    fun onRoute(f: Float): Offset {
        val goal = f.coerceIn(0f, 1f) * routeLength.last()
        for (i in 1 until route.size) if (routeLength[i] >= goal) {
            val u = (goal - routeLength[i - 1]) / max(1e-3f, routeLength[i] - routeLength[i - 1])
            return route[i - 1] + (route[i] - route[i - 1]) * u
        }
        return route.last()
    }

    // The 34 touch areas exactly as SensorBoard draws them: sensor units, y down, outer radius 154 = the round area.
    val sensors: List<List<Offset>> = run {
        fun xy(r: Float, a: Float) = Offset(r * cos(a * PI.toFloat() / 180f), r * sin(a * PI.toFloat() / 180f))
        fun arc(r: Float, start: Float, sweep: Float, n: Int) = (0..n).map { xy(r, start + sweep * it / n) }
        fun rot(p: List<Offset>, deg: Float): List<Offset> { val c = cos(deg * PI.toFloat() / 180f); val s = sin(deg * PI.toFloat() / 180f)
            return p.map { Offset(it.x * c - it.y * s, it.x * s + it.y * c) } }
        fun regular(c: Offset, r: Float, n: Int, start: Float) = (0 until n).map { c + xy(r, start + it * 360f / n) }
        (0 until 34).map { i ->
            when {
                i == 16 || i == 17 -> { val sd = if (i == 16) 1f else -1f
                    listOf(Offset(sd, -29f), Offset(12 * sd, -29f), Offset(29 * sd, -12f), Offset(29 * sd, 12f), Offset(12 * sd, 29f), Offset(sd, 29f)) }
                else -> {
                    val group = "ABCDE"[when { i < 8 -> 0; i < 16 -> 1; i < 26 -> 3; else -> 4 }]
                    val n = when (group) { 'A' -> i; 'B' -> i - 8; 'D' -> i - 18; else -> i - 26 }
                    val angle = n * 45f + (if (group == 'A' || group == 'B') 22.5f else 0f) - 90f
                    val radius = when (group) { 'A' -> 127f; 'B' -> 61f; 'D' -> 131f; else -> 90f }
                    when (group) {
                        'B' -> regular(xy(radius, angle), 23f, 8, 22.5f)
                        'E' -> regular(xy(radius, angle), 21f, 4, angle)
                        'D' -> rot(arc(154f, -97.2f, 14.4f, 6) + listOf(Offset(11.8f, -101f), Offset(0f, -113f), Offset(-11.8f, -101f)), n * 45f)
                        else -> rot(arc(154f, -81.6f, 28.2f, 10) + listOf(Offset(59.4f, -80.3f), Offset(44.8f, -80.3f), Offset(25.4f, -88.6f), Offset(15.6f, -98.8f)), n * 45f)
                    }
                }
            }
        }
    }
    val sensorRadius = sensors.map { pts -> pts.fold(Offset.Zero) { a, b -> a + b }.div(pts.size.toFloat()).getDistance() }

    fun circle(c: Offset, r: Float, n: Int) = (0 until n).map { val a = 2 * PI.toFloat() * it / n; Offset(c.x + r * cos(a), c.y + r * sin(a)) }

    fun roundRect(cx: Float, cy: Float, w: Float, h: Float, r: Float, segs: Int) =
        roundPolygon(listOf(Offset(cx - w / 2, cy - h / 2), Offset(cx + w / 2, cy - h / 2), Offset(cx + w / 2, cy + h / 2), Offset(cx - w / 2, cy + h / 2)), r, segs)

    /** Rounds every corner of a counter-clockwise polygon (y up) with arcs of radius r. */
    fun roundPolygon(points: List<Offset>, r: Float, segs: Int): List<Offset> {
        val out = ArrayList<Offset>()
        for (i in points.indices) {
            val p = points[i]; val a = points[(i + points.size - 1) % points.size]; val b = points[(i + 1) % points.size]
            val u = (a - p) / (a - p).getDistance(); val v = (b - p) / (b - p).getDistance()
            val half = kotlin.math.acos((u.x * v.x + u.y * v.y).coerceIn(-1f, 1f)) / 2
            val tangent = min(r / tan(half), min((a - p).getDistance(), (b - p).getDistance()) / 2)
            val radius = tangent * tan(half)
            val bis = (u + v) / (u + v).getDistance()
            val c = p + bis * (radius / sin(half))
            val s0 = atan2(p.y + u.y * tangent - c.y, p.x + u.x * tangent - c.x)
            val e0 = atan2(p.y + v.y * tangent - c.y, p.x + v.x * tangent - c.x)
            var sweep = e0 - s0
            while (sweep > PI) sweep -= 2 * PI.toFloat()
            while (sweep < -PI) sweep += 2 * PI.toFloat()
            for (k in 0..segs) { val ang = s0 + sweep * k / segs; out += Offset(c.x + radius * cos(ang), c.y + radius * sin(ang)) }
        }
        return out
    }
}

private fun ease(x: Float) = Settle.transform(x.coerceIn(0f, 1f))
private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
private fun ledColor(i: Int, t: Float): Color {
    val pos = ((i / 8f + t / 8f) % 1f) * LED_COLORS.size; val k = pos.toInt() % LED_COLORS.size
    return lerp(LED_COLORS[k], LED_COLORS[(k + 1) % LED_COLORS.size], pos - pos.toInt())
}

private fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
/** Convex hull (monotone chain) of points already on the canvas. */
private fun hull(points: List<Offset>): List<Offset> {
    val p = points.sortedWith(compareBy<Offset>({ it.x }, { it.y }))
    if (p.size < 3) return p
    val lower = ArrayList<Offset>(); val upper = ArrayList<Offset>()
    for (q in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], q) <= 0f) lower.removeAt(lower.size - 1); lower += q }
    for (q in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], q) <= 0f) upper.removeAt(upper.size - 1); upper += q }
    lower.removeAt(lower.size - 1); upper.removeAt(upper.size - 1)
    return lower + upper
}
private fun polygon(points: List<Offset>) = Path().apply { moveTo(points[0].x, points[0].y); for (i in 1 until points.size) lineTo(points[i].x, points[i].y); close() }

/**
 * The outside wall of a convex part from [back] to [front]: one shape spanning both outlines, in the side
 * colour. A part with an opening ([hole], seen at depth [holeZ]) keeps it open, so the wall never covers it.
 */
private fun DrawScope.wall(v: PlateView, outline: List<Offset>, back: Float, front: Float, side: Color, hole: List<Offset>? = null, holeZ: Float = front) {
    val shape = polygon(hull(v.projected(outline, back) + v.projected(outline, front)))
    if (hole != null) { v.path(hole, holeZ, shape); shape.fillType = PathFillType.EvenOdd }
    drawPath(shape, side)
}
/** The inner wall of an opening from [front] down to [back], visible only through the opening itself. */
private fun DrawScope.holeWall(v: PlateView, hole: List<Offset>, back: Float, front: Float, side: Color) {
    val near = v.path(hole, front)
    clipPath(near) { drawPath(Path().apply { addPath(near); addPath(v.path(hole, back)); fillType = PathFillType.EvenOdd }, side) }
}
/** Draws [block] at [alpha] as one piece (an offscreen layer only while it is fading). */
private fun DrawScope.fading(alpha: Float, block: () -> Unit) {
    if (alpha <= 0.002f) return
    if (alpha >= 0.998f) { block(); return }
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

/**
 * One frame. The entrance assembles the controller: the body rises from below and fades in, then the frame,
 * the ring and each key settle onto it from a little nearer, and a light runs round the keys once.
 */
private fun DrawScope.drawSetup(s: MotionShot, fx: Float, fy: Float, zoom: Float, yaw: Float, pitch: Float, labels: MotionLabels, measurer: TextMeasurer) {
    val base = min(size.height * 0.9f / 440f, size.width * 0.86f / 300f)
    val p = s.intro
    val swing = 1 - ease(p / 0.9f)
    val v = PlateView(size.width / 2, size.height / 2, fx, fy, base * zoom, yaw - 13f * swing, pitch + 8f * swing)
    val bodyIn = ease(p / 0.4f)
    fading(bodyIn) { drawBody(s, v.lifted(-26f * (1 - bodyIn))) }
    val frameIn = ease((p - 0.25f) / 0.35f)
    fading(frameIn) { drawFrame(s, v, 22f * (1 - frameIn)) }
    val ringIn = ease((p - 0.35f) / 0.35f)
    fading(ringIn) { drawRing(s, v, 18f * (1 - ringIn)) }
    drawKeys(s, v)
    if (s.outline > 0.01f) {
        // The built-in screen's extent, faintly, so both openings read as one screen.
        drawPath(v.path(PlateGeo.monitor, PlateGeo.SCREEN_Z), s.brand.copy(alpha = 0.6f * s.outline), style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
    }
    if (s.phone > 0.01f) drawConnection(s, v)
    if (s.scene == MotionScene.DONE) drawRingFlow(s, v)
    if (s.fadeEdges && zoom > 1.05f) {
        // A close framing runs past the hero's edges; let it melt into the page instead of cutting off.
        val h = 22.dp.toPx(); val top = if (s.dark) Color(0xFF10121A) else Color(0xFFF4F6F9); val bottom = if (s.dark) Color(0xFF14171F) else Color(0xFFEEF1F5)
        drawRect(Brush.verticalGradient(listOf(top, top.copy(alpha = 0f)), 0f, h), Offset.Zero, Size(size.width, h))
        drawRect(Brush.verticalGradient(listOf(bottom.copy(alpha = 0f), bottom), size.height - h, size.height), Offset(0f, size.height - h), Size(size.width, h))
    }
    drawLabels(s, v, labels, measurer)
}

/** Floor shadow, ears, the body and its port, the built-in screen with what shows on it, and the panel's face. */
private fun DrawScope.drawBody(s: MotionShot, v: PlateView) {
    val stroke = 1.dp.toPx()
    val line = if (s.dark) Color(0xFF9AA0AA) else LINE
    val side = Color(0xFFDFE2E8)
    val foot = v.pt(0f, -240f, -17f); val r = PlateGeo.W * 1.08f * v.s
    if (r > 1f) scale(1f, 0.12f, pivot = foot) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = if (s.dark) 0.5f else 0.16f), Color.Transparent), foot, r), r, foot)
    }
    for (ear in PlateGeo.ears) {
        wall(v, ear, PlateGeo.BODY_BACK + 4f, -2f, side)
        val e = v.path(ear, -2f); drawPath(e, Color.White); drawPath(e, line, style = Stroke(stroke))
    }
    for (c in PlateGeo.speakers) {
        drawPath(v.circle(c, 10.5f, -1.9f, n = 32), Color(0xFF1E2024))
        val p = v.pt(c, -1.8f); val dome = 6.4f * v.s
        if (dome > 0.5f) drawPath(v.circle(c, 6.4f, -1.8f, n = 24),
            Brush.radialGradient(listOf(Color(0xFFF4F5F7), Color(0xFFA9AEB6)), p + Offset(-dome * 0.25f, -dome * 0.3f), dome * 1.4f))
    }
    wall(v, PlateGeo.body, PlateGeo.BODY_BACK, 0f, side)
    drawPortSlot(v)
    drawScreen(s, v)
    // The controller's own screen already shows the touch areas when it is mirrored.
    if (s.view == null) drawTouch(s, v)
    val panel = v.path(PlateGeo.body, 0f).apply { v.path(PlateGeo.window, 0f, this); v.circle(PlateGeo.SCREEN, PlateGeo.SCREEN_R, 0f, this); fillType = PathFillType.EvenOdd }
    drawPath(panel, Brush.linearGradient(listOf(Color.White, Color(0xFFF3F4F7)), v.pt(-PlateGeo.W, 165f), v.pt(PlateGeo.W, -225f)))
    drawPath(panel, line, style = Stroke(stroke))
}

/** The acrylic frame round the top window (with the window's inner wall down to the screen), the start button and the Aime reader. */
private fun DrawScope.drawFrame(s: MotionShot, v: PlateView, rise: Float) {
    val stroke = 1.dp.toPx()
    val line = if (s.dark) Color(0xFF9AA0AA) else LINE
    val top = PlateGeo.FRAME_Z + rise
    wall(v, PlateGeo.frame, rise, top, Color(0xFFD8CFE8), hole = PlateGeo.window)
    val acrylic = v.path(PlateGeo.frame, top).apply { v.path(PlateGeo.window, top, this); fillType = PathFillType.EvenOdd }
    drawPath(acrylic, Brush.linearGradient(listOf(Color(0xFFFFD9EC), Color(0xFFE6DAFF), Color(0xFFD2ECFF)), v.pt(-PlateGeo.W, 172f, top), v.pt(PlateGeo.W, 40f, top)))
    drawPath(acrylic, line, style = Stroke(stroke))
    holeWall(v, PlateGeo.window, PlateGeo.SCREEN_Z, top, Color(0xFFCFD3DB))
    val trim = PlateGeo.TRIM_Z + rise
    wall(v, PlateGeo.start, rise, trim, Color(0xFF1F5FCC)); drawPath(v.path(PlateGeo.start, trim), Color(0xFF2F7BF6))
    wall(v, PlateGeo.aimeEdge, rise, trim, Color(0xFFE39BB8)); drawPath(v.path(PlateGeo.aimeEdge, trim), Color(0xFFF6B9D0))
    drawPath(v.path(PlateGeo.aimeInner, trim + 0.1f), Color.White)
    if (s.aime > 0.01f) {
        val pulse = s.aime * (0.5f + 0.5f * sin(s.t * 2 * PI.toFloat() / 1.6f))
        drawPath(v.path(PlateGeo.aimeInner, trim + 0.1f), Color(0xFFFF7BB0).copy(alpha = 0.6f * pulse))
    }
}

/** The key ring round the round opening, with the opening's inner wall down to the screen. */
private fun DrawScope.drawRing(s: MotionShot, v: PlateView, rise: Float) {
    val line = if (s.dark) Color(0xFF9AA0AA) else LINE
    val top = PlateGeo.RING_Z + rise
    wall(v, PlateGeo.ringOuter, rise, top, Color(0xFFD9DCE3), hole = PlateGeo.ringInner)
    val ring = v.path(PlateGeo.ringOuter, top).apply { v.path(PlateGeo.ringInner, top, this); fillType = PathFillType.EvenOdd }
    drawPath(ring, Color(0xFFEEF0F4)); drawPath(ring, line, style = Stroke(1.dp.toPx()))
    holeWall(v, PlateGeo.ringInner, PlateGeo.SCREEN_Z, top, Color(0xFFCFD3DB))
}

/** The USB-C port on the left side wall; it shows only while that wall faces the viewer. */
private fun DrawScope.drawPortSlot(v: PlateView) {
    if (v.yawSin < 0.03f) return
    drawPath(polygon(PlateGeo.portSlot.map { v.pt(-PlateGeo.W - 0.3f, it.x, it.y) }), Color(0xFF2B2E35).copy(alpha = smooth(0.03f, 0.12f, v.yawSin)))
}

/**
 * The built-in screen: during setup, exactly what the controller's own screen shows (see [drawMirror]);
 * otherwise dark glass when idle and a game picture across both openings when output is on.
 */
private fun DrawScope.drawScreen(s: MotionShot, v: PlateView) {
    val z = PlateGeo.SCREEN_Z
    drawPath(v.path(PlateGeo.monitor, z), Color(0xFF1B1D22))
    val view = s.view
    if (view != null) { drawMirror(s, v, view); return }
    val display = v.path(PlateGeo.display, z)
    val top = v.pt(0f, PlateGeo.MON_TOP, z); val bottom = v.pt(0f, PlateGeo.MON_BOTTOM, z)
    drawPath(display, Brush.linearGradient(listOf(Color(0xFF2A2E37), Color(0xFF15171C)), top, bottom))
    if (s.picture < 0.01f) return
    val centre = v.pt(0f, (PlateGeo.MON_TOP + PlateGeo.MON_BOTTOM) / 2, z)
    clipPath(display) {
        rotate(s.turn, pivot = centre) {
            val shift = if (s.still) 0f else (s.t / 8f) % 1f
            val len = (bottom.y - top.y) * 2
            val from = Offset(top.x, top.y - shift * len)
            val big = Rect(centre, max(size.width, size.height))
            drawRect(Brush.linearGradient(listOf(Color(0xFFFF8CC6), Color(0xFF9B7BFF), Color(0xFF52D2FF), Color(0xFFFF8CC6)), from,
                Offset(from.x, from.y + len), TileMode.Repeated), big.topLeft, big.size, alpha = s.picture)
            // A few shapes so the picture has an up: a bar at the top, the play ring and notes in the round area.
            drawPath(v.path(PlateGeo.roundRect(0f, 140f, 160f, 9f, 4.5f, 3), z), Color.White.copy(alpha = 0.75f * s.picture))
            drawPath(v.circle(PlateGeo.SCREEN, 66f, z), Color.White.copy(alpha = 0.75f * s.picture), style = Stroke(1.2.dp.toPx()))
            for (a in listOf(60f, 160f, 290f)) {
                val q = PlateGeo.SCREEN + Offset(66f * cos(a * PI.toFloat() / 180f), 66f * sin(a * PI.toFloat() / 180f))
                drawPath(v.circle(q, 5f, z, n = 16), Color.White.copy(alpha = 0.9f * s.picture))
            }
        }
    }
}

/** Ring keys: the entrance, lighting preview, the input check's guide, real presses, release waves and checked marks. */
private fun DrawScope.drawKeys(s: MotionShot, v: PlateView) {
    val stroke = 1.dp.toPx()
    val line = if (s.dark) Color(0xFF9AA0AA) else LINE
    val live = s.signals.live && s.signals.buttonsLinked
    val anyInput = live && (s.checkedButtons != 0 || s.signals.buttons != 0)
    // The entrance's last touch: a light runs round the keys once as they settle.
    val sweep = if (s.intro < 1f) (s.intro - 0.62f) / 0.38f * 9f - 0.5f else -99f
    for (i in 0 until 8) {
        val arrive = if (s.intro < 1f) ease((s.intro - 0.45f - 0.03f * i) / 0.25f) else 1f
        if (arrive <= 0.002f) continue
        val c = PlateGeo.keyCenters[i]
        val cap = if (arrive < 1f) PlateGeo.keys[i].map { c + (it - c) * (0.8f + 0.2f * arrive) } else PlateGeo.keys[i]
        var fill = Color(0xFFFAFAFC); var edge = line
        var glow = 0f
        if (s.led > 0.01f) {
            // Lighting preview: one colour run round the ring when the step opens, then a quiet breath.
            val level = if (s.still) 0.6f else if (s.since < 2.4f) {
                val head = s.since / 2.4f * 8f; val d = abs(head - i)
                0.25f + 0.75f * exp(-d * d / 1.2f)
            } else 0.45f + 0.25f * sin(s.t * 2 * PI.toFloat() / 3f - i * PI.toFloat() / 4)
            fill = lerp(fill, ledColor(i, s.t), 0.55f * level * s.led); glow = 0.3f * s.led
        }
        if (sweep > -50f) {
            val d = sweep - i; val k = exp(-d * d / 0.8f)
            fill = lerp(fill, ledColor(i, 0f), 0.6f * k); glow = max(glow, 0.35f * k)
        }
        var pressed = 0f
        if (s.keys > 0.01f && live) {
            if (s.checkedButtons shr i and 1 == 1) { fill = lerp(fill, Color(0xFFEAF2FF), s.keys); edge = lerp(edge, Color(0xFF9CC0FF), s.keys) }
            if (s.signals.buttons shr i and 1 == 1) {
                pressed = ((s.nanos - s.pressAt[i]) / 80e6f).coerceIn(0f, 1f) * s.keys
                fill = lerp(fill, s.brand, pressed); edge = lerp(edge, s.brand, pressed)
            }
        }
        // Keys settle from 14 mm nearer in the entrance; a pressed key sinks 3 mm.
        val face = PlateGeo.KEY_Z + 14f * (1 - arrive) - 3f * pressed
        fading(arrive) {
            wall(v, cap, PlateGeo.RING_Z, face, lerp(Color(0xFFD3D7DF), s.brand, 0.5f * pressed))
            val path = v.path(cap, face)
            drawPath(path, fill); drawPath(path, edge, style = Stroke(stroke))
        }
        if (glow > 0.01f) {
            val gc = v.pt(c, face); val rr = 30f * v.s
            if (rr > 1f) drawCircle(Brush.radialGradient(listOf((if (s.led > 0.01f) ledColor(i, s.t) else ledColor(i, 0f)).copy(alpha = glow), Color.Transparent), gc, rr), rr, gc)
        }
    }
    if (s.keys < 0.01f) return
    if (!anyInput) {
        // Guide only: dashed outlines, one after another round the ring. Not a test result.
        val head = if (s.still) -1 else ((s.t / 0.4f).toInt() % 8)
        for (i in 0 until 8) {
            val c = if (i == head) s.brand.copy(alpha = 0.85f * s.keys) else MUTED.copy(alpha = 0.55f * s.keys)
            drawPath(v.path(PlateGeo.keys[i], PlateGeo.KEY_Z), c, style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
        }
        return
    }
    for (i in 0 until 8) {
        if (s.releaseAt[i] != Long.MIN_VALUE) {
            val age = (s.nanos - s.releaseAt[i]) / 300e6f
            if (age in 0f..1f) drawPath(v.circle(PlateGeo.keyCenters[i], 20f + 18f * ease(age), PlateGeo.KEY_Z, n = 40),
                s.brand.copy(alpha = 0.45f * (1 - age) * s.keys), style = Stroke(1.2.dp.toPx()))
        }
        if (s.checkedButtons shr i and 1 == 1) {
            val a = (67.5f - 45f * i) * PI.toFloat() / 180f
            drawCircle(s.brand.copy(alpha = s.keys), 2.6.dp.toPx(), v.pt(PlateGeo.SCREEN.x + 126f * cos(a), PlateGeo.SCREEN.y + 126f * sin(a), PlateGeo.RING_Z))
        }
    }
}

/** The 34 touch areas on the screen in the round opening: a slow guide wave, real touches, areas already checked. */
private fun DrawScope.drawTouch(s: MotionShot, v: PlateView) {
    if (s.touch < 0.01f) return
    val k = PlateGeo.SCREEN_R / 154f
    val live = s.signals.live && s.signals.touchLinked
    val wave = if (s.still || (live && (s.checkedTouches != 0L || s.signals.touches != 0L))) -999f else (s.t % 2.4f) / 2.4f * 190f - 20f
    for (i in 0 until 34) {
        // Sensor units are y down; the model is y up.
        val path = v.path(PlateGeo.sensors[i].map { Offset(PlateGeo.SCREEN.x + it.x * k, PlateGeo.SCREEN.y - it.y * k) }, PlateGeo.SCREEN_Z + 0.2f)
        val pressed = live && (s.signals.touches shr i and 1L) == 1L
        val checked = live && (s.checkedTouches shr i and 1L) == 1L
        val guide = exp(-((PlateGeo.sensorRadius[i] - wave) / 28f).let { it * it })
        when {
            pressed -> drawPath(path, s.brand.copy(alpha = 0.85f * s.touch))
            checked -> drawPath(path, s.brand.copy(alpha = 0.3f * s.touch))
            else -> drawPath(path, Color.White.copy(alpha = (0.07f + 0.16f * guide) * s.touch))
        }
        drawPath(path, Color.White.copy(alpha = 0.45f * s.touch), style = Stroke(0.8.dp.toPx()))
    }
}

/** Phone, cable and plugs. The cable's look follows the real link; without one it is a dotted guide. */
private fun DrawScope.drawConnection(s: MotionShot, v: PlateView) {
    val a = s.phone
    val stroke = 1.dp.toPx()
    val line = if (s.dark) Color(0xFF9AA0AA) else LINE
    // Phone: thin bezel, neutral screen (it is the user's own phone), a little thickness, its bottom port.
    wall(v, PlateGeo.phoneBody, PlateGeo.PHONE_BACK, PlateGeo.PHONE_FACE, Color(0xFFD5D9E0).copy(alpha = a))
    val body = v.path(PlateGeo.phoneBody, PlateGeo.PHONE_FACE)
    drawPath(body, Color.White.copy(alpha = a)); drawPath(body, line.copy(alpha = a), style = Stroke(1.2f * stroke))
    drawPath(v.path(PlateGeo.phoneScreen, PlateGeo.PHONE_FACE + 0.1f), Brush.linearGradient(listOf(Color(0xFFF1F4F9), Color(0xFFE3E8F1)),
        v.pt(PlateGeo.PHONE.x - 40f, 46f, PlateGeo.PHONE_FACE), v.pt(PlateGeo.PHONE.x + 40f, -126f, PlateGeo.PHONE_FACE)), alpha = a)
    drawPath(v.path(PlateGeo.roundRect(PlateGeo.PHONE.x, PlateGeo.PHONE.y + PlateGeo.PHONE_H / 2 - 9f, 24f, 6f, 3f, 3), PlateGeo.PHONE_FACE + 0.2f), Color(0xFFD5DAE3).copy(alpha = a))
    val link = if (s.signals.live) s.signals.link else LinkState.WAITING
    val total = PlateGeo.routeLength.last()
    val route = Path().apply {
        for ((i, p) in PlateGeo.route.withIndex()) { val q = v.pt(p, PlateGeo.routeZ(PlateGeo.routeLength[i] / total)); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
    }
    val cableWidth = max(2.dp.toPx(), 4f * v.s)
    val port = v.pt(PlateGeo.PORT, PlateGeo.PORT_Z)
    val plugZ = (PlateGeo.PHONE_BACK + PlateGeo.PHONE_FACE) / 2
    fun plugs(fill: Color, edge: Color, dashed: Boolean) {
        for ((p, z) in listOf(PlateGeo.phonePlug to plugZ, PlateGeo.portPlug to PlateGeo.PORT_Z)) {
            val path = v.path(p, z)
            if (dashed) drawPath(path, edge.copy(alpha = a), style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))))
            else { drawPath(path, fill.copy(alpha = a)); drawPath(path, edge.copy(alpha = a), style = Stroke(1.2f * stroke)) }
        }
    }
    fun dot(f: Float, alpha: Float, halo: Float) {
        val p = v.pt(PlateGeo.onRoute(f), PlateGeo.routeZ(f))
        drawCircle(s.brand.copy(alpha = 0.16f * alpha * a), halo, p); drawCircle(s.brand.copy(alpha = alpha * a), 3.4.dp.toPx(), p)
    }
    when (link) {
        LinkState.WAITING -> {
            // Nothing plugged in: dotted guide and dotted plugs, a light travelling to the port every 2.4 s.
            drawPath(route, MUTED.copy(alpha = 0.55f * a), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 5.dp.toPx()))))
            plugs(Color.White, MUTED, dashed = true)
            val f = if (s.still) 0.5f else (s.t % 2.4f) / 2.4f
            dot(f, smooth(0f, 0.1f, f) * (1 - smooth(0.9f, 1f, f)), 9.dp.toPx())
        }
        LinkState.PERMISSION -> {
            // A device is attached and the app asked for permission: quiet grey cable, the light waiting by the port.
            drawPath(route, line.copy(alpha = a), style = Stroke(cableWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            plugs(Color.White, line, dashed = false)
            val breath = if (s.still) 0.5f else 0.5f + 0.5f * sin(s.t * 2 * PI.toFloat() / 3f)
            dot(0.93f, 1f, (9f + 4f * breath).dp.toPx())
        }
        LinkState.CONNECTED, LinkState.PARTIAL -> {
            // Only on the real link: the light reaches the port (200 ms), then the whole path lights (400 ms).
            val arrive = if (s.still) 1f else ease(s.linkSince / 0.2f)
            val lit = if (s.still) 1f else ease((s.linkSince - 0.2f) / 0.4f)
            drawPath(route, lerp(line, s.brand, lit).copy(alpha = a), style = Stroke(cableWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            plugs(Color.White, lerp(line, s.brand, lit), dashed = false)
            if (arrive < 1f) dot(0.93f + 0.07f * arrive, 1f, 9.dp.toPx())
            val glow = 18f * v.s
            if (link == LinkState.PARTIAL) drawCircle(AMBER.copy(alpha = lit * a), 10f * v.s, port, style = Stroke(1.6.dp.toPx()))
            else if (glow > 1f) drawCircle(Brush.radialGradient(listOf(s.brand.copy(alpha = 0.35f * lit * a), Color.Transparent), port, glow), glow, port)
        }
        LinkState.FAILED -> {
            // The light fades out (600 ms) and the port is marked once (800 ms); nothing turns red or shakes.
            val fade = if (s.still) 1f else ease(s.linkSince / 0.6f)
            drawPath(route, line.copy(alpha = a * (1 - 0.45f * fade)), style = Stroke(cableWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            plugs(Color.White, line, dashed = false)
            if (fade < 1f) dot(0.93f, 1 - fade, 9.dp.toPx())
            val mark = if (s.still) 0.6f else 0.35f + 0.65f * (1 - smooth(0.4f, 1.4f, s.linkSince))
            drawCircle(AMBER.copy(alpha = mark * a), 10f * v.s, port, style = Stroke(1.6.dp.toPx()))
        }
    }
}

/** Done: one quiet light round the outer ring. */
private fun DrawScope.drawRingFlow(s: MotionShot, v: PlateView) {
    val p = if (s.still) 1f else s.since / 1.2f
    if (p >= 1f) return
    val start = 90f - 360f * ease(p)
    val arc = Path().apply {
        for (k in 0..24) {
            val a = (start - 100f * k / 24) * PI.toFloat() / 180f
            val q = v.pt(PlateGeo.SCREEN.x + 120f * cos(a), PlateGeo.SCREEN.y + 120f * sin(a), PlateGeo.RING_Z)
            if (k == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
        }
    }
    drawPath(arc, s.brand.copy(alpha = sin(PI.toFloat() * p)), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
}

/** Callouts and small tags, drawn over everything else. */
private fun DrawScope.drawLabels(s: MotionShot, v: PlateView, labels: MotionLabels, measurer: TextMeasurer) {
    val style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    fun appear(delay: Float) = if (s.still) 1f else ((s.since - delay) / 0.7f).coerceIn(0f, 1f)
    if (s.phone > 0.01f) callout(v.pt(PlateGeo.PORT.x - 0.3f, PlateGeo.PORT.y, PlateGeo.PORT_Z), -1, -44f, measurer.measure(labels.usb, style),
        Color(0xFF3482FF), CalloutGlyph.PLUG, s, appear(0.3f), s.phone)
    if (s.led > 0.01f) {
        callout(v.pt(PlateGeo.keyCenters[5], PlateGeo.KEY_Z), -1, 44f, measurer.measure(labels.keys, style), Color(0xFFA071FF), CalloutGlyph.RING, s, appear(0.3f), s.led)
    }
    if (s.aime > 0.01f) callout(v.pt(PlateGeo.AIME, PlateGeo.TRIM_Z), 1, -40f, measurer.measure(labels.aime, style), Color(0xFFFF5FA8), CalloutGlyph.NFC, s, 1f, s.aime)
    if (s.outline > 0.01f) callout(v.pt(PlateGeo.MON_HW, PlateGeo.MON_TOP, PlateGeo.SCREEN_Z), 1, -22f, measurer.measure(labels.screen, style),
        Color(0xFF2FAEF0), CalloutGlyph.SCREEN, s, appear(0.3f), s.outline)
    if (s.scene == MotionScene.DONE) {
        val shown = if (s.still) 1f else smooth(0.8f, 1.2f, s.since)
        val w = measurer.measure(labels.ready, style).size.width + 24.dp.toPx()
        tag(labels.ready, Offset(size.width - 16.dp.toPx() - w, 12.dp.toPx()), measurer, s.brand, shown)
    }
}

private enum class CalloutGlyph { PLUG, RING, NFC, SCREEN }

/** Marker, a short angled line towards the label, then a pill with a round icon: drawn in that order. */
private fun DrawScope.callout(anchor: Offset, side: Int, dyDp: Float, text: TextLayoutResult, accent: Color, glyph: CalloutGlyph, s: MotionShot, progress: Float, alpha: Float) {
    if (progress <= 0f || alpha <= 0.01f) return
    val pill = 28.dp.toPx(); val icon = 18.dp.toPx(); val pad = 6.dp.toPx(); val margin = 6.dp.toPx()
    val width = pad + icon + pad + text.size.width + pad * 1.6f
    val y = (anchor.y + dyDp.dp.toPx()).coerceIn(pill / 2 + margin, size.height - pill / 2 - margin)
    val left = if (side < 0) margin else size.width - margin - width
    val inner = if (side < 0) left + width else left
    var ex = anchor.x + side * 20.dp.toPx()
    ex = if (side < 0) max(ex, inner + 8.dp.toPx()) else min(ex, inner - 8.dp.toPx())
    val elbow = Offset(ex, y); val end = Offset(inner, y)
    val ink = (if (s.dark) Color.White else Color(0xFF242A38)).copy(alpha = (if (s.dark) 0.7f else 0.5f) * alpha)
    val marker = smooth(0f, 0.2f, progress)
    drawCircle(Color.White.copy(alpha = alpha * marker), 5.dp.toPx(), anchor)
    drawCircle(accent.copy(alpha = alpha * marker), 3.2.dp.toPx(), anchor)
    val l1 = smooth(0.2f, 0.45f, progress); val l2 = smooth(0.45f, 0.65f, progress)
    if (l1 > 0f) drawLine(ink, anchor, anchor + (elbow - anchor) * l1, 1.2.dp.toPx(), StrokeCap.Round)
    if (l2 > 0f) drawLine(ink, elbow, elbow + (end - elbow) * l2, 1.2.dp.toPx(), StrokeCap.Round)
    val la = smooth(0.6f, 1f, progress) * alpha
    if (la <= 0.01f) return
    val top = y - pill / 2; val corner = CornerRadius(pill / 2)
    drawRoundRect(Color.Black.copy(alpha = (if (s.dark) 0.35f else 0.07f) * la), Offset(left, top + 2.dp.toPx()), Size(width, pill), corner)
    drawRoundRect((if (s.dark) Color(0xFF1E212B) else Color.White).copy(alpha = 0.97f * la), Offset(left, top), Size(width, pill), corner)
    drawRoundRect((if (s.dark) Color.White else Color.Black).copy(alpha = 0.07f * la), Offset(left, top), Size(width, pill), corner, style = Stroke(1.dp.toPx()))
    val ic = Offset(left + pad + icon / 2, y)
    drawCircle(accent.copy(alpha = la), icon / 2, ic)
    glyph(glyph, ic, icon / 2, Color.White.copy(alpha = la))
    drawText(text, color = s.ink, topLeft = Offset(left + pad + icon + pad, y - text.size.height / 2f), alpha = la)
}

private fun DrawScope.glyph(g: CalloutGlyph, c: Offset, r: Float, ink: Color) {
    val st = Stroke(r * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (g) {
        CalloutGlyph.PLUG -> drawRoundRect(ink, Offset(c.x - r * 0.5f, c.y - r * 0.22f), Size(r, r * 0.44f), CornerRadius(r * 0.22f), style = st)
        CalloutGlyph.RING -> { drawCircle(ink, r * 0.48f, c, style = st); drawCircle(ink, r * 0.13f, c) }
        CalloutGlyph.NFC -> for (k in 1..3) { val rr = r * 0.22f * k
            drawArc(ink, -50f, 100f, false, Offset(c.x - r * 0.35f - rr, c.y - rr), Size(rr * 2, rr * 2), style = st) }
        CalloutGlyph.SCREEN -> drawRoundRect(ink, Offset(c.x - r * 0.3f, c.y - r * 0.5f), Size(r * 0.6f, r), CornerRadius(r * 0.1f), style = st)
    }
}

private fun DrawScope.tag(text: String, at: Offset, measurer: TextMeasurer, brand: Color, alpha: Float) {
    if (alpha <= 0.01f) return
    val layout = measurer.measure(text, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    val h = 22.dp.toPx(); val w = layout.size.width + 24.dp.toPx()
    drawRoundRect(brand.copy(alpha = 0.12f * alpha), at, Size(w, h), CornerRadius(h / 2))
    drawText(layout, color = brand, topLeft = Offset(at.x + 12.dp.toPx(), at.y + (h - layout.size.height) / 2), alpha = alpha)
}

/** The setup pages' backdrop: a quiet vertical gradient that lets the controller stand out. */
internal fun DrawScope.drawShowcase(dark: Boolean) {
    drawRect(Brush.verticalGradient(if (dark) listOf(Color(0xFF0D0F14), Color(0xFF171A23)) else listOf(Color(0xFFF8F9FC), Color(0xFFEBEEF4))))
}

/**
 * ColorOS-style backdrop: three large pastel glows (the acrylic frame's pink, lavender and sky) drifting
 * slowly over the page colour.
 */
internal fun DrawScope.drawAurora(t: Float, dark: Boolean) {
    drawRect(if (dark) Color(0xFF111215) else Color(0xFFFBFBFD))
    if (size.width < 1f) return
    val w = size.width; val h = size.height; val a = if (dark) 0.30f else 0.70f
    val orbs = listOf(
        Triple(Color(0xFFFFC6E4), Offset(w * (0.18f + 0.07f * sin(t / 7f)), h * (0.22f + 0.04f * cos(t / 9f))), w * 0.78f),
        Triple(Color(0xFFD9CCFF), Offset(w * (0.88f + 0.06f * cos(t / 8f)), h * (0.46f + 0.05f * sin(t / 6f))), w * 0.82f),
        Triple(Color(0xFFC3E8FF), Offset(w * (0.30f + 0.08f * sin(t / 10f)), h * (0.86f + 0.03f * cos(t / 7f))), w * 0.9f))
    for ((color, center, radius) in orbs)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = 0f)), center, radius), radius, center)
}

/* ---------------- Setup on the controller itself: its screen and its lights ---------------- */

/**
 * What setup is showing, for the controller: the step and its choices, the live signals, and the words on the
 * phone's page (eyebrow, heading, body, status and its [tone]: 0 quiet, 1 info, 2 waiting, 3 done, 4 warning),
 * so the controller's own screen says the same thing. Its lights follow the same values (SetupLights).
 */
internal data class SetupView(
    val step: Int, val clockwise: Boolean, val external: Boolean, val leds: Boolean,
    val signals: SetupSignals, val inputView: InputView, val checkedButtons: Int, val checkedTouches: Long,
    val eyebrow: String, val heading: String, val body: String, val status: String, val tone: Int,
    /**
     * Setup's detailed settings are open. The rotation is theirs to change then: setup's own [clockwise] may be
     * older than a choice made there, so it must not turn the screen (it takes that choice over when they close).
     */
    val rotationHeld: Boolean = false,
) {
    /** The same view with the screen turned as it really is, for showing while the rotation is held. */
    fun turned(clockwise: Boolean) = copy(clockwise = clockwise)
    /** The link as a SetupLink state. */
    val linkCode: Int get() = if (!signals.live) SetupLink.NOT_LOOKING else when (signals.link) {
        LinkState.WAITING -> SetupLink.LOOKING; LinkState.PERMISSION -> SetupLink.PERMISSION; LinkState.CONNECTED -> SetupLink.CONNECTED
        LinkState.PARTIAL -> SetupLink.PARTIAL; LinkState.FAILED -> SetupLink.FAILED
    }
    val touchView: Boolean get() = inputView == InputView.TOUCH
    /** Buttons and touch areas really held now; nothing while that input is not connected. */
    val pressed: Int get() = if (signals.live && signals.buttonsLinked) signals.buttons and 0xFF else 0
    val touched: Long get() = if (signals.live && signals.touchLinked) signals.touches else 0L
    fun light(stepMs: Long, linkMs: Long, i: Int): Int =
        SetupLights.color(step, linkCode, pressed, checkedButtons, touched, touchView, leds, stepMs, linkMs, i)
}

private val SCREEN_SURFACE = Color(0xFF121315)
private val SCREEN_TEXT = Color(0xFFEDEAE4)
private val SCREEN_TEXT_2 = Color(0xFF8F8A83)
private val SCREEN_TRACK = Color(0xFF24262A)
private val SCREEN_ZONE = Color(0xFF1C1E22)
private val SCREEN_ACCENT = Color(0xFF277AF7)
private val SCREEN_AMBER = Color(0xFFF2B45A)
private val SCREEN_OK = Color(0xFF5AD08F)
private val CIRCLE = Offset(540f, 1380f)

/**
 * Setup's page on the controller's built-in screen, laid out like the game's portrait frame (1080 x 1920: the
 * top panel, and the circle seen through the round opening) and turned a quarter turn the way setup's rotation
 * choice says, exactly as the game will be; a new choice turns it at once. The panel carries the phone page's
 * words. The circle shows the step on the controller itself: the connection state, the 34 touch areas under
 * the fingers and the eight buttons round the rim during the input check, the lighting preview, which way is
 * up, and done. Round the rim, eight lights show the colours the buttons' own LEDs show.
 */
@Composable
internal fun ExternalSetupScreen(view: SetupView?) {
    val still = motionStill()
    val turn by animateFloatAsState(if (view?.clockwise == true) 90f else -90f, tween(if (still) 0 else 520, easing = Settle), label = "turn")
    val measurer = rememberTextMeasurer()
    // Milliseconds for the lights and the small motions, outside snapshot state: only the drawing reads [frames].
    val clock = remember { LongArray(1) }
    var frames by remember { mutableIntStateOf(0) }
    // Drawn again only while something on the page moves, and then at most 30 times a second.
    val moving by rememberUpdatedState(view)
    val stepAtOf = remember { LongArray(1) }
    val linkAtOf = remember { LongArray(1) }
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        val offset = withFrameNanos { it } / 1_000_000 - clock[0]
        // Far enough back to draw at once, without the overflow that Long.MIN_VALUE would give in "now - drawn".
        var drawn = -1_000_000L
        while (true) withFrameNanos {
            clock[0] = it / 1_000_000 - offset
            val v = moving
            if (v != null && clock[0] - drawn >= 33 && screenMoving(v, clock[0] - stepAtOf[0], clock[0] - linkAtOf[0])) { drawn = clock[0]; frames++ }
        }
    }
    val stepAt = remember(view?.step) { clock[0] }.also { stepAtOf[0] = it }
    val linkAt = remember(view?.linkCode) { clock[0] }.also { linkAtOf[0] = it }
    val words = screenWords(view)
    val layer = rememberGraphicsLayer()
    val recorded = remember { arrayOfNulls<Any>(1) }
    val recordedAt = remember { LongArray(1) { -1_000_000L } }
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = view?.heading ?: "Oniimai" }) {
        drawRect(Color.Black)
        if (view == null) return@Canvas
        frames
        val now = clock[0]
        val key = listOf(view, words.progress, words.language, still)
        if (recorded[0] != key || (screenMoving(view, now - stepAt, now - linkAt) && now - recordedAt[0] >= 33)) {
            layer.record(this, layoutDirection, IntSize(1080, 1920)) { drawSetupFrame(measurer, view, now - stepAt, now - linkAt, words, still) }
            recorded[0] = key; recordedAt[0] = now
        }
        // The portrait frame fitted into the landscape display after a quarter turn (DisplayGeometry).
        val k = min(size.width / 1920f, size.height / 1080f)
        translate(size.width / 2, size.height / 2) {
            rotate(turn, Offset.Zero) {
                scale(k, k, Offset.Zero) {
                    translate(-540f, -960f) { drawLayer(layer) }
                }
            }
        }
    }
}

private class ScreenWords(val progress: String, val language: String, val up: String, val upright: String)

/** The controller screen's own words for [view]: where setup is, the language's name, and which way is up. */
private fun screenWords(view: SetupView?) = ScreenWords(
    if (view != null && view.step in SetupLights.FIRST..SetupLights.LAST) str(Msg.SETTINGS_SETUP) + " · ${view.step + 1} / 5" else str(Msg.SETTINGS_SETUP),
    if (view?.step == SetupLights.LANGUAGE) I18n.NAMES[I18n.index()] else "", str(Msg.SETUP_SCREEN_UP), str(Msg.SETUP_SCREEN_UPRIGHT))

/** One frame in game units: the panel and the circle in deep charcoal, warm white type, one blue accent. */
private fun DrawScope.drawSetupFrame(m: TextMeasurer, v: SetupView, stepMs: Long, linkMs: Long, w: ScreenWords, still: Boolean) {
    drawSetupPanel(m, v, w)
    drawSetupCircle(m, v, stepMs, linkMs, w, still)
}

/**
 * On the phone's model, the controller's own screen as it shows now: the panel (1080 x 450 game units) through
 * the top window and the circle through the round opening, each fitted to its opening and following the
 * model's angle. Choosing a rotation turns both a quarter turn until they stand upright.
 */
private fun DrawScope.drawMirror(s: MotionShot, v: PlateView, view: SetupView) {
    val z = PlateGeo.SCREEN_Z
    val m = s.mirror
    // Recorded only when the words change; the circle again at most 15 times a second while something on it moves.
    val panelKey = listOf(view.eyebrow, view.heading, view.body, view.status, view.tone, view.step, s.words.progress)
    if (m.panelKey != panelKey) {
        m.panel.record(this, layoutDirection, IntSize(1080, 450)) { drawSetupPanel(s.measurer, view, s.words) }
        m.panelKey = panelKey
    }
    val circleKey = listOf(view, s.words.language, s.still)
    if (m.circleKey != circleKey || (screenMoving(view, s.viewMs, s.linkMs) && s.nanos - m.circleAt >= 66_000_000L)) {
        m.circle.record(this, layoutDirection, IntSize(1080, 1080)) { translate(0f, -840f) { drawSetupCircle(s.measurer, view, s.viewMs, s.linkMs, s.words, s.still) } }
        m.circleKey = circleKey; m.circleAt = s.nanos
    }
    val k = 210f / 1080f
    clipPath(v.path(PlateGeo.window, z)) {
        withTransform({ transform(affine(v, z, 540f, 225f) { x, y -> Offset(-105f + x * k, 106f + (225f - y) * k) }); rotate(s.turn, Offset(540f, 225f)) }) {
            drawRect(SCREEN_SURFACE, Offset(-540f, -540f), Size(2160f, 1530f))
            drawLayer(m.panel)
        }
    }
    val c = PlateGeo.SCREEN_R / 540f
    clipPath(v.circle(PlateGeo.SCREEN, PlateGeo.SCREEN_R, z)) {
        withTransform({ transform(affine(v, z, 540f, 1380f) { x, y -> Offset(PlateGeo.SCREEN.x + (x - 540f) * c, PlateGeo.SCREEN.y - (y - 1380f) * c) }); rotate(s.turn, CIRCLE) }) {
            translate(0f, 840f) { drawLayer(m.circle) }
        }
    }
}

/** The mirror's recorded pictures, and what they were recorded for. */
private class MirrorLayers(val panel: GraphicsLayer, val circle: GraphicsLayer) {
    var panelKey: Any? = null
    var circleKey: Any? = null
    var circleAt = -1_000_000_000L
}

/**
 * Whether anything on the controller's page moves now (its lights or marks), so it has to be drawn again;
 * otherwise one recording serves until what setup shows changes.
 */
private fun screenMoving(v: SetupView, stepMs: Long, linkMs: Long): Boolean = when (v.step) {
    SetupLights.WELCOME, SetupLights.LANGUAGE, SetupLights.LIGHTING -> v.leds
    SetupLights.CONNECT -> (v.linkCode != SetupLink.CONNECTED && v.linkCode != SetupLink.FAILED) || linkMs < SetupLights.SETTLE_MS + 300
    SetupLights.DONE -> stepMs < SetupLights.SETTLE_MS + 300
    else -> false
}

/**
 * The affine map that follows [v]'s projection for a flat picture at depth [z] around (x0, y0), given where
 * picture units land on the model. The lens is long, so one affine map per opening is close enough.
 */
private fun affine(v: PlateView, z: Float, x0: Float, y0: Float, model: (Float, Float) -> Offset): Matrix {
    val span = 400f
    val o = v.pt(model(x0, y0), z)
    val ux = (v.pt(model(x0 + span, y0), z) - o) / span; val uy = (v.pt(model(x0, y0 + span), z) - o) / span
    return Matrix().apply {
        this[0, 0] = ux.x; this[0, 1] = ux.y; this[1, 0] = uy.x; this[1, 1] = uy.y
        this[3, 0] = o.x - x0 * ux.x - y0 * uy.x; this[3, 1] = o.y - x0 * ux.y - y0 * uy.y
    }
}

/** The top panel: status bar, the phone page's words and progress. */
private fun DrawScope.drawSetupPanel(m: TextMeasurer, v: SetupView, w: ScreenWords) {
    drawRect(SCREEN_SURFACE, Offset.Zero, Size(1080f, 450f))
    // Status bar: the ring mark and the name, and where setup is.
    drawCircle(SCREEN_TEXT, 12f, Offset(80f, 50f), style = Stroke(3f)); drawCircle(SCREEN_TEXT, 4f, Offset(80f, 50f))
    screenText(m, "Oniimai", 104f, 50f, 26f, SCREEN_TEXT, bold = true, align = -1, width = 400f)
    screenText(m, w.progress, 1024f, 50f, 26f, SCREEN_TEXT_2, align = 1, width = 520f)
    // The phone page's words; during the input check its count replaces the explanation.
    if (v.eyebrow.isNotEmpty()) screenText(m, v.eyebrow, 540f, 112f, 26f, SCREEN_ACCENT, bold = true, width = 968f, lines = 1)
    screenText(m, v.heading, 540f, 196f, 54f, SCREEN_TEXT, bold = true, width = 968f)
    if (v.step == SetupLights.INPUT) screenText(m, v.status, 540f, 306f, 30f, toneColor(v.tone, SCREEN_TEXT), width = 920f)
    else screenText(m, v.body, 540f, 306f, 28f, SCREEN_TEXT_2, width = 920f)
    if (v.step in SetupLights.FIRST..SetupLights.LAST) for (i in 0..4) {
        val x = 540f - 2 * 72f + i * 72f
        drawRoundRect(if (i <= v.step - SetupLights.FIRST) SCREEN_ACCENT else SCREEN_TRACK, Offset(x - 26f, 396f), Size(52f, 8f), CornerRadius(4f))
    }
}

/** The circle: what this step is about, on the controller itself, with the buttons' lights round the rim. */
private fun DrawScope.drawSetupCircle(m: TextMeasurer, v: SetupView, stepMs: Long, linkMs: Long, w: ScreenWords, still: Boolean) {
    drawCircle(SCREEN_SURFACE, 540f, CIRCLE)
    when (v.step) {
        SetupLights.LANGUAGE -> screenText(m, w.language, 540f, 1330f, 96f, SCREEN_TEXT, bold = true, width = 860f, lines = 1)
        SetupLights.CONNECT -> drawLinkMark(v.linkCode, linkMs, still)
        SetupLights.INPUT -> if (v.touchView) drawZones(v) else screenText(m, "${v.checkedButtons.countOneBits()} / 8", 540f, 1360f, 120f, SCREEN_TEXT, bold = true, width = 600f, lines = 1)
        SetupLights.LIGHTING -> drawLightRing(v, stepMs, linkMs)
        SetupLights.SCREEN -> {
            // Which way is up on this screen: an arrow and its words; upright means the choice is right.
            val arrow = Path().apply { moveTo(540f, 1470f); lineTo(540f, 1150f); moveTo(440f, 1250f); lineTo(540f, 1150f); lineTo(640f, 1250f) }
            drawPath(arrow, SCREEN_ACCENT, style = Stroke(18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            screenText(m, w.up, 540f, 1570f, 56f, SCREEN_TEXT, bold = true, width = 760f)
            screenText(m, w.upright, 540f, 1652f, 28f, SCREEN_TEXT_2, width = 700f)
        }
        SetupLights.DONE -> drawCheck(SCREEN_ACCENT, 1.0f)
        // The welcome leaves the circle to its words, in the middle.
        else -> screenText(m, v.status, 540f, 1380f, 36f, SCREEN_TEXT_2, width = 760f)
    }
    // The status under the circle's middle (in the middle, inside the lighting ring), wherever the circle is not
    // full of touch areas or the arrow.
    if (v.step != SetupLights.INPUT && v.step != SetupLights.SCREEN && v.step != SetupLights.WELCOME && v.status.isNotEmpty()) {
        val y = if (v.step == SetupLights.LIGHTING) 1380f else 1640f
        val color = toneColor(v.tone, SCREEN_TEXT_2)
        if (v.tone != 0) drawCircle(color, 7f, Offset(540f, y - 52f))
        screenText(m, v.status, 540f, y, 30f, if (v.tone == 0) SCREEN_TEXT_2 else SCREEN_TEXT, width = if (v.step == SetupLights.LIGHTING) 820f else 760f)
    }
    // The touch check keeps the whole circle for the touch areas, and the lighting step has its own ring;
    // the buttons' own lights still follow both.
    if (!(v.step == SetupLights.INPUT && v.touchView) && v.step != SetupLights.LIGHTING) drawRimLights(v, stepMs, linkMs)
}

private fun toneColor(tone: Int, quiet: Color) = when (tone) { 4 -> SCREEN_AMBER; 3 -> SCREEN_OK; 1, 2 -> SCREEN_ACCENT; else -> quiet }

/** Button i sits outside the round opening at this angle (degrees, y down): button 1 upper right, clockwise. */
private fun buttonAngle(i: Int) = i * 45f - 67.5f

/** Eight lights just inside the rim, by the buttons, in the colours the buttons' LEDs show now. */
private fun DrawScope.drawRimLights(v: SetupView, stepMs: Long, linkMs: Long) {
    val box = Rect(CIRCLE.x - 500f, CIRCLE.y - 500f, CIRCLE.x + 500f, CIRCLE.y + 500f)
    for (i in 0 until 8) {
        val rgb = v.light(stepMs, linkMs, i)
        val color = Color(0xFF000000.toInt() or rgb)
        drawArc(SCREEN_TRACK, buttonAngle(i) - 17f, 34f, false, box.topLeft, box.size, style = Stroke(22f, cap = StrokeCap.Round))
        if (rgb != 0) {
            drawArc(color.copy(alpha = 0.25f), buttonAngle(i) - 19f, 38f, false, box.topLeft, box.size, style = Stroke(44f, cap = StrokeCap.Round))
            drawArc(color, buttonAngle(i) - 17f, 34f, false, box.topLeft, box.size, style = Stroke(22f, cap = StrokeCap.Round))
        }
    }
}

/** Looking: a dotted ring with a light going round. Permission: a breathing ring. Connected: a check. Partial or failed: "!". */
private fun DrawScope.drawLinkMark(link: Int, ms: Long, still: Boolean) {
    val c = Offset(540f, 1330f)
    when (link) {
        SetupLink.CONNECTED -> drawCheck(SCREEN_ACCENT, if (still) 1f else (ms / 300f).coerceIn(0f, 1f))
        SetupLink.PARTIAL, SetupLink.FAILED -> {
            drawCircle(SCREEN_AMBER, 150f, c, style = Stroke(12f))
            drawLine(SCREEN_AMBER, Offset(540f, 1250f), Offset(540f, 1345f), 18f, StrokeCap.Round)
            drawCircle(SCREEN_AMBER, 12f, Offset(540f, 1405f))
        }
        SetupLink.PERMISSION -> {
            val breath = if (still) 0.6f else 0.35f + 0.5f * (0.5f + 0.5f * cos(ms / 3000f * 2 * PI.toFloat()))
            drawCircle(SCREEN_ACCENT.copy(alpha = breath), 150f, c, style = Stroke(12f))
            drawCircle(SCREEN_ACCENT.copy(alpha = breath), 26f, c)
        }
        else -> {
            drawCircle(SCREEN_TEXT_2, 150f, c, style = Stroke(6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 22f))))
            val a = (if (still) 0f else (ms % SetupLights.TRAVEL_MS) / SetupLights.TRAVEL_MS.toFloat() * 360f) - 67.5f
            val r = a * PI.toFloat() / 180f
            drawCircle(SCREEN_ACCENT, 16f, Offset(c.x + 150f * cos(r), c.y + 150f * sin(r)))
        }
    }
}

/** A filled accent disc with a white check, drawn in as [p] goes 0 to 1. */
private fun DrawScope.drawCheck(color: Color, p: Float) {
    val c = Offset(540f, 1330f)
    drawCircle(color.copy(alpha = 0.9f * p), 150f * (0.85f + 0.15f * p), c)
    val tick = Path().apply { moveTo(470f, 1335f); lineTo(522f, 1387f); lineTo(618f, 1281f) }
    if (p > 0.3f) drawPath(tick, Color.White.copy(alpha = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)), style = Stroke(22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The 34 touch areas exactly where the sensors are: held areas bright, checked ones tinted, the rest dark. */
private fun DrawScope.drawZones(v: SetupView) {
    val k = 540f / 154f
    for (i in 0 until 34) {
        // Sensor units are y down, as on this screen.
        val path = Path().apply {
            PlateGeo.sensors[i].forEachIndexed { n, p -> val q = Offset(CIRCLE.x + p.x * k, CIRCLE.y + p.y * k); if (n == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
            close()
        }
        val fill = when {
            v.touched shr i and 1L == 1L -> SCREEN_ACCENT
            v.checkedTouches shr i and 1L == 1L -> SCREEN_ACCENT.copy(alpha = 0.35f)
            else -> SCREEN_ZONE
        }
        drawPath(path, fill)
        drawPath(path, SCREEN_TRACK, style = Stroke(4f))
    }
}

/**
 * The lighting preview as one band of light just inside the rim, as on the game's welcome screen: each button's
 * colour sits where that button is and blends into its neighbours, with a soft glow and a faint spill of the
 * average colour onto the circle.
 */
private fun DrawScope.drawLightRing(v: SetupView, stepMs: Long, linkMs: Long) {
    val rgb = IntArray(8) { v.light(stepMs, linkMs, it) }
    fun color(c: Int) = Color(0xFF000000.toInt() or c)
    // Sweep stops from 0 (3 o'clock) clockwise, one per button. Buttons sit at 22.5° either side of 3 o'clock, so
    // the seam there gets the colour halfway between those two.
    val stops = (0 until 8).map { i -> (((buttonAngle(i) % 360f) + 360f) % 360f / 360f) to color(rgb[i]) }.sortedBy { it.first }
    val seam = lerp(stops.last().second, stops.first().second, 0.5f)
    val sweep = Brush.sweepGradient(*(listOf(0f to seam) + stops + listOf(1f to seam)).toTypedArray(), center = CIRCLE)
    val average = color(SetupLights.average(rgb))
    drawCircle(Brush.radialGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 0.9f to average.copy(alpha = 0.16f), 1f to Color.Transparent,
        center = CIRCLE, radius = 520f), 520f, CIRCLE)
    for ((width, alpha) in listOf(64f to 0.08f, 40f to 0.12f, 22f to 0.22f, 8f to 1f))
        drawCircle(sweep, 452f, CIRCLE, alpha = alpha, style = Stroke(width))
}

/** Text centred on [y]; [align] -1 starts at [x], 0 centres on it, 1 ends at it. Sizes are game units. */
private fun DrawScope.screenText(m: TextMeasurer, text: String, x: Float, y: Float, size: Float, color: Color,
                                 bold: Boolean = false, align: Int = 0, width: Float, lines: Int = 2) {
    if (text.isEmpty()) return
    val style = TextStyle(fontSize = (size / (density * fontScale)).sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = color,
        textAlign = when (align) { -1 -> TextAlign.Start; 1 -> TextAlign.End; else -> TextAlign.Center })
    // A fixed width, so the alignment applies across it (with only a maximum, the line is laid out at its own width).
    val layout = m.measure(text, style, constraints = androidx.compose.ui.unit.Constraints.fixedWidth(width.toInt()), maxLines = lines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, density = this)
    val left = when (align) { -1 -> x; 1 -> x - width; else -> x - width / 2 }
    drawText(layout, topLeft = Offset(left, y - layout.size.height / 2f))
}
