package com.edgeore.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.os.PowerManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import com.edgeore.app.R
import com.edgeore.app.contribution.SessionPhase
import com.edgeore.app.ui.theme.EdgeColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/** What the Mine art shows. Derived only from whether a contribution session is ACTIVE; not a separate app state. */
enum class MiningPose { RESTING, SWINGING, STILL_ON }

/**
 * Timing and pose of the Mine screen pickaxe animation, as pure functions of the cycle phase t in [0, 1).
 * It swings only while a real contribution session is ACTIVE (see contribution/ContributionSession.kt).
 */
object MiningMotion {
    const val CYCLE_MS = 1000
    /** Phase at which the pick tip meets the rock. */
    const val IMPACT = 0.60f
    /** Degrees the pickaxe is raised from the strike pose (clockwise about the grip). */
    const val REST_DEG = 24f
    const val WIND_UP_DEG = 42f
    const val REBOUND_DEG = 14f
    private const val CARRY_DEG = 10f

    /** Swinging only while a session is ACTIVE; idle, starting, stopping and failed sessions rest. */
    fun pose(phase: SessionPhase, reducedMotion: Boolean): MiningPose =
        if (phase == SessionPhase.ACTIVE) (if (reducedMotion) MiningPose.STILL_ON else MiningPose.SWINGING) else MiningPose.RESTING

    private fun easeInOut(x: Float) = x * x * (3 - 2 * x)
    private fun easeIn(x: Float) = x * x * x
    private fun easeOut(x: Float) = 1 - (1 - x) * (1 - x)
    private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    /** Wind-up (slow), strike (fast), rebound, then carry back toward the next wind-up. 0 = touching the rock. */
    fun swingDegrees(t: Float): Float = when {
        t < 0.45f -> CARRY_DEG + (WIND_UP_DEG - CARRY_DEG) * easeInOut(seg(t, 0f, 0.45f))
        t < IMPACT -> WIND_UP_DEG * (1 - easeIn(seg(t, 0.45f, IMPACT)))
        t < 0.74f -> REBOUND_DEG * easeOut(seg(t, IMPACT, 0.74f))
        else -> REBOUND_DEG + (CARRY_DEG - REBOUND_DEG) * easeInOut(seg(t, 0.74f, 1f))
    }

    private fun sinceImpact(t: Float): Float? = (t - IMPACT).takeIf { it >= 0f }

    /** Rock shake, -1..1 of the shake amplitude, decaying over ~0.12 of a cycle after impact. */
    fun shake(t: Float): Float = sinceImpact(t)?.let { d -> if (d > 0.14f) 0f else (sin(d * 2 * PI * 28).toFloat() * exp(-d * 30)) } ?: 0f

    /** Mint vein glow 0..1: a brief pulse at impact. */
    fun glow(t: Float): Float = sinceImpact(t)?.let { d -> exp(-d * 9).toFloat() } ?: 0f

    /** Spark life 0..1 after impact, or null when no sparks are in the air. */
    fun sparks(t: Float): Float? = sinceImpact(t)?.let { d -> (d / 0.24f).takeIf { it <= 1f } }

    fun description(pose: MiningPose): String = when (pose) {
        MiningPose.SWINGING -> "Pickaxe striking the rock: a contribution session is running on your own node and storage gateway. Nothing is earned."
        MiningPose.STILL_ON -> "Pickaxe against the rock, not moving because animations are off: a contribution session is running. Nothing is earned."
        MiningPose.RESTING -> "Pickaxe at rest beside the rock: no contribution session is running."
    }
}

/** Test hook: the swing angle drawn in the current frame. */
val MiningSwingDegreesKey = SemanticsPropertyKey<Float>("MiningSwingDegrees")
private var SemanticsPropertyReceiver.miningSwing by MiningSwingDegreesKey

/** Test and preview seam: a fixed cycle phase instead of the running clock. Never provided by the app. */
internal val LocalMiningPhaseOverride = staticCompositionLocalOf<Float?> { null }

/** True when the system "Remove animations" setting (animator duration scale 0) is on. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The Mine screen art: the user's rock with the pickaxe pivoted near its grip. Swings about once a second while a
 * contribution session is ACTIVE; rests at a tilt otherwise; holds still when the system removes animations.
 */
@Composable
fun MiningAnimation(
    phase: SessionPhase,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var visible by remember { mutableStateOf(true) }
    val savingPower = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    val displayedPhase = if (visible && lifecycleState.isAtLeast(Lifecycle.State.STARTED)) phase else SessionPhase.IDLE
    val pose = MiningMotion.pose(displayedPhase, reducedMotion || savingPower)
    val rock = ImageBitmap.imageResource(R.drawable.mine_rock)
    val veins = ImageBitmap.imageResource(R.drawable.mine_rock_veins)
    val pick = ImageBitmap.imageResource(R.drawable.mine_pickaxe)
    val fixedPhase = LocalMiningPhaseOverride.current
    val cycle: Float? = when {
        pose != MiningPose.SWINGING -> null
        fixedPhase != null -> fixedPhase
        else -> {
            val transition = rememberInfiniteTransition(label = "mine-swing")
            val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(MiningMotion.CYCLE_MS, easing = LinearEasing), RepeatMode.Restart), label = "phase")
            t
        }
    }
    val angle = when (pose) {
        MiningPose.SWINGING -> MiningMotion.swingDegrees(cycle ?: 0f)
        MiningPose.STILL_ON -> 0f
        MiningPose.RESTING -> MiningMotion.REST_DEG
    }
    Canvas(
        modifier.onGloballyPositioned { coordinates ->
            val b = coordinates.boundsInWindow()
            visible = b.bottom > 0 && b.top < view.rootView.height && b.right > 0 && b.left < view.rootView.width
        }.testTag("mining-animation").semantics {
            contentDescription = MiningMotion.description(pose)
            stateDescription = pose.name
            miningSwing = angle
        },
    ) {
        val t = cycle
        val radius = minOf(size.width, size.height) * 0.46f
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.radialGradient(listOf(EdgeColors.mint.copy(alpha = 0.16f), Color.Transparent), center, radius * 1.2f), radius * 1.2f, center)
        drawCircle(EdgeColors.mint.copy(alpha = 0.24f), radius, center, style = Stroke(1.dp.toPx()))
        drawCircle(EdgeColors.mint, radius * 0.90f, center, style = Stroke(1.4.dp.toPx()))
        drawCircle(EdgeColors.mintSoft.copy(alpha = 0.60f), radius * 0.80f, center, style = Stroke(1.dp.toPx()))
        listOf(0f, 90f, 210f).forEach { deg ->
            val a = deg * PI.toFloat() / 180f
            drawCircle(EdgeColors.mintSoft, 3.dp.toPx(), center + Offset(cos(a) * radius * 0.9f, sin(a) * radius * 0.9f))
        }
        clipPath(Path().apply { addOval(Rect(center - Offset(radius * 0.77f, radius * 0.77f), center + Offset(radius * 0.77f, radius * 0.77f))) }) {
            val art = radius * 1.40f
            translate(center.x - art / 2, center.y - art / 2) {
                val outer = size
                // Existing geometry uses the draw scope size; scale a compact art layer into the ring.
                withTransform({ scale(art / outer.width, art / outer.height, pivot = Offset.Zero) }) {
                    drawMine(rock, veins, pick, angle, t?.let(MiningMotion::shake) ?: 0f,
                        t?.let(MiningMotion::glow) ?: if (pose == MiningPose.STILL_ON) 0.25f else 0f,
                        t?.let(MiningMotion::sparks))
                }
            }
        }
    }
}

// Geometry in fractions of the exported drawables (res/drawable-nodpi, see docs/design/mine-animation.md).
private const val PIVOT_X = 0.90f   // grip, near the handle end
private const val PIVOT_Y = 0.88f
private const val TIP_X = 0.03f     // lower pick tip
private const val TIP_Y = 0.54f
private const val TARGET_X = 0.60f  // strike point on the big rock's shoulder
private const val TARGET_Y = 0.24f

private val sparkAngles = floatArrayOf(-150f, -125f, -100f, -75f, -55f, -35f, -165f)

private fun DrawScope.drawMine(rock: ImageBitmap, veins: ImageBitmap, pick: ImageBitmap, angle: Float, shake: Float, glow: Float, sparkLife: Float?) {
    val w = size.width
    val h = size.height
    val rw = w * 0.66f
    val rh = rw * rock.height / rock.width
    val rockLeft = w * 0.5f - rw / 2 - w * 0.13f
    val rockTop = h - rh
    val dst = IntSize(rw.toInt(), rh.toInt())
    val dstOff = IntOffset((rockLeft + shake * 3.dp.toPx()).toInt(), rockTop.toInt()) // only the rock shakes
    drawImage(rock, dstOffset = dstOff, dstSize = dst)
    if (glow > 0.01f) {
        drawImage(veins, dstOffset = dstOff, dstSize = dst, alpha = glow.coerceIn(0f, 1f),
            colorFilter = ColorFilter.tint(EdgeColors.mint), blendMode = BlendMode.Plus)
    }

    val pw = w * 0.48f
    val ph = pw * pick.height / pick.width
    val target = Offset(rockLeft + rw * TARGET_X, rockTop + rh * TARGET_Y)
    val tipVec = Offset((TIP_X - PIVOT_X) * pw, (TIP_Y - PIVOT_Y) * ph)
    val reach = hypot(tipVec.x, tipVec.y)
    // Pivot sits to the right of the strike point, slightly below it, one handle-length away, so the raised pick stays inside the frame.
    val dir = 28f * PI.toFloat() / 180f
    val pivot = Offset(target.x + reach * cos(dir), target.y + reach * sin(dir))
    val strikeRotation = (atan2(target.y - pivot.y, target.x - pivot.x) - atan2(tipVec.y, tipVec.x)) * 180f / PI.toFloat()

    rotate(strikeRotation + angle, pivot) {
        drawImage(pick, dstOffset = IntOffset((pivot.x - PIVOT_X * pw).toInt(), (pivot.y - PIVOT_Y * ph).toInt()), dstSize = IntSize(pw.toInt(), ph.toInt()))
    }

    if (sparkLife != null) {
        val life = sparkLife
        val r0 = 3.2.dp.toPx() * (1 - life * 0.7f)
        sparkAngles.forEachIndexed { i, deg ->
            val a = deg * PI.toFloat() / 180f
            val speed = (42 + (i * 7) % 18).dp.toPx()
            val gravity = 40.dp.toPx()
            val p = Offset(target.x + cos(a) * speed * life, target.y + sin(a) * speed * life + gravity * life * life)
            val c = if (i % 2 == 0) EdgeColors.copper else EdgeColors.mint
            translate(p.x, p.y) { drawCircle(c, radius = r0, center = Offset.Zero, alpha = 1 - life) }
        }
    }
}
