package com.edgeore.app.screens

import androidx.test.core.app.ApplicationProvider
import com.edgeore.app.ui.Onboarding
import org.junit.rules.ExternalResource

/** Marks the first-run introduction as already read (or not) before the Activity launches. Order it before the Compose rule. */
class OnboardingSeen(private val seen: Boolean = true) : ExternalResource() {
    override fun before() { Onboarding.set(ApplicationProvider.getApplicationContext(), seen) }
}
