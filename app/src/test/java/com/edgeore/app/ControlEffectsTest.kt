package com.edgeore.app

import com.edgeore.app.device.CpuBudget
import com.edgeore.app.device.DeviceSnapshot
import com.edgeore.app.device.EdgePolicy
import com.edgeore.app.device.ResourceSettings
import com.edgeore.app.device.ThermalLevel
import com.edgeore.app.settings.Control
import com.edgeore.app.settings.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 "settings enforcement labels": every control on a screen states its real effect, and the label matches the code. */
class ControlEffectsTest {
    private val screens = File("src/main/java/com/edgeore/app/ui/screens")
    private val controlCall = Regex("""^\s*(Switch|Slider|ResourceControl|LabeledSlider|PrivacySwitch)\(""", RegexOption.MULTILINE)
    private val helperDef = Regex("""private fun (LabeledSlider|PrivacySwitch)\(.*?\n}\n""", RegexOption.DOT_MATCHES_ALL)

    private fun source(screen: String) = File(screens, "${screen.lowercase().replaceFirstChar { it.uppercase() }}Screen.kt").readText()

    @Test fun everyControlIsShownOnItsScreen() {
        for (c in Control.entries) {
            assertTrue("${c.name} missing on ${c.screen}", source(c.screen).contains("EffectNote(Control.${c.name}"))
            assertTrue(c.detail.isNotBlank())
        }
    }

    @Test fun everySwitchAndSliderHasAnEffectNote() {
        val files = screens.listFiles { f -> f.name.endsWith("Screen.kt") }!!.sortedBy { it.name }
        var total = 0
        for (f in files) {
            val body = helperDef.replace(f.readText(), "")
            val controls = controlCall.findAll(body).count()
            val notes = Regex("""EffectNote\(Control\.""").findAll(body).count()
            assertEquals("controls vs effect notes in ${f.name}", controls, notes)
            total += controls
        }
        assertEquals("one Control entry per control on screen", Control.entries.size, total)
    }

    @Test fun cpuLimitIsSavedOnlyAndSaysSo() {
        assertEquals(Effect.SAVED_ONLY, Control.CPU_LIMIT.effect)
        assertFalse(CpuBudget.DEVICE_WIDE_CAP_ENFORCED)
        assertEquals(CpuBudget.storedBudgetLabel(40), Control.cpuDetail(40))
        // Changing the CPU limit changes nothing that EdgePolicy decides.
        val d = DeviceSnapshot(80, true, ThermalLevel.NONE, 0)
        assertEquals(
            EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true, cpuLimitPercent = 10), d),
            EdgePolicy.evaluate(ResourceSettings(edgeModeResumed = true, cpuLimitPercent = 100), d),
        )
    }

    @Test fun enforcedGatesReallyBlock() {
        val on = ResourceSettings(edgeModeResumed = true)
        assertEquals(Effect.ENFORCED, Control.CHARGE_ONLY.effect)
        assertFalse(EdgePolicy.evaluate(on, DeviceSnapshot(80, false, ThermalLevel.NONE, 0)).gatesPass)
        assertTrue(EdgePolicy.evaluate(on.copy(chargeOnly = false), DeviceSnapshot(80, false, ThermalLevel.NONE, 0)).gatesPass)
        assertEquals(Effect.ENFORCED, Control.THERMAL_GUARD.effect)
        assertFalse(EdgePolicy.evaluate(on, DeviceSnapshot(80, true, ThermalLevel.MODERATE, 0)).gatesPass)
        assertFalse(EdgePolicy.evaluate(on, DeviceSnapshot(80, true, null, 0)).gatesPass)
        assertEquals(Effect.ENFORCED, Control.BATTERY_RESERVE.effect)
        assertFalse(EdgePolicy.evaluate(on.copy(batteryReservePercent = 90), DeviceSnapshot(80, true, ThermalLevel.NONE, 0)).gatesPass)
    }

    @Test fun mechanismsThatDoNotExistAreNotLabelledEnforced() {
        for (c in listOf(Control.KILL_SWITCH, Control.INCLUDE_LOCATION)) assertEquals(Effect.UNAVAILABLE, c.effect)
        for (c in listOf(Control.SHARING_CONSENT, Control.SHARING_QUOTA, Control.AI_MEMORY_LIMIT)) assertEquals(Effect.SAVED_ONLY, c.effect)
    }
}
