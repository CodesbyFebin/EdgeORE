package com.edgeore.app

import androidx.test.platform.app.InstrumentationRegistry
import com.edgeore.app.ui.Onboarding
import org.junit.rules.ExternalResource

/** Device twin of the JVM OnboardingSeen rule: skips the first-run introduction so the walk starts on the tabs. */
class OnboardingSeenRule : ExternalResource() {
    override fun before() { Onboarding.set(InstrumentationRegistry.getInstrumentation().targetContext, true) }
}
