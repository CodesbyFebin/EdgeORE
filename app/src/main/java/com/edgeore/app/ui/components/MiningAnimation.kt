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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.ImageBitmap
import com.edgeore.app.R
import com.edgeore.app.contribution.SessionPhase
import kotlin.math.PI
import kotlin.math.exp
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

/** Frames of the rock-and-pickaxe loop (res/drawable-nodpi/mine_frame_NN.webp, cut from the user's GIF; see docs/design/mine-animation.md). */
object MiningFrames {
    const val COUNT = 25
    /** Idle and failed sessions hold the raised-pick frame; nothing moves. */
    const val REST = 0
    /** The pick meets the rock in this frame (MiningMotion.IMPACT * COUNT); held when animations are off. */
    const val IMPACT_FRAME = 15
    val ids = intArrayOf(
        R.drawable.mine_frame_00, R.drawable.mine_frame_01, R.drawable.mine_frame_02, R.drawable.mine_frame_03, R.drawable.mine_frame_04,
        R.drawable.mine_frame_05, R.drawable.mine_frame_06, R.drawable.mine_frame_07, R.drawable.mine_frame_08, R.drawable.mine_frame_09,
        R.drawable.mine_frame_10, R.drawable.mine_frame_11, R.drawable.mine_frame_12, R.drawable.mine_frame_13, R.drawable.mine_frame_14,
        R.drawable.mine_frame_15, R.drawable.mine_frame_16, R.drawable.mine_frame_17, R.drawable.mine_frame_18, R.drawable.mine_frame_19,
        R.drawable.mine_frame_20, R.drawable.mine_frame_21, R.drawable.mine_frame_22, R.drawable.mine_frame_23, R.drawable.mine_frame_24,
    )

    fun frameAt(pose: MiningPose, cycle: Float?): Int = when (pose) {
        MiningPose.RESTING -> REST
        MiningPose.STILL_ON -> IMPACT_FRAME
        MiningPose.SWINGING -> ((cycle ?: 0f) * COUNT).toInt().coerceIn(0, COUNT - 1)
    }
}

/** Test hook: the frame index drawn. */
val MiningFrameKey = SemanticsPropertyKey<Int>("MiningFrame")
private var SemanticsPropertyReceiver.miningFrame by MiningFrameKey

/**
 * The Mine screen art: the user's rock-and-pickaxe loop, played at 40 ms a frame (one strike a second) only while a
 * contribution session is ACTIVE; held on the raised-pick frame otherwise; held on the impact frame when the system
 * removes animations.
 */
@Composable
fun MiningAnimation(
    phase: SessionPhase,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val pose = MiningMotion.pose(phase, reducedMotion)
    val res = LocalContext.current.resources
    val frames = remember(res) { MiningFrames.ids.map { ImageBitmap.imageResource(res, it) } }
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
    val index = MiningFrames.frameAt(pose, cycle)
    Canvas(
        modifier.testTag("mining-animation").semantics {
            contentDescription = MiningMotion.description(pose)
            stateDescription = pose.name
            miningSwing = angle
            miningFrame = index
        },
    ) {
        val img = frames[index]
        val scale = minOf(size.width / img.width, size.height / img.height)
        val w = (img.width * scale).toInt(); val h = (img.height * scale).toInt()
        drawImage(img, dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()), dstSize = IntSize(w, h),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
    }
}
