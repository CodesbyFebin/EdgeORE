package com.edgeore.app

import com.edgeore.app.notifications.OreReminders
import org.junit.Assert.*
import org.junit.Test

class OreRemindersTest {
    @Test fun consentQuietHoursAndDailyDeduplicationAreAllRequired() {
        assertFalse(OreReminders.canDeliver(false, 10, "2026-10-12", null))
        assertFalse(OreReminders.canDeliver(true, 8, "2026-10-12", null))
        assertFalse(OreReminders.canDeliver(true, 21, "2026-10-12", null))
        assertFalse(OreReminders.canDeliver(true, 10, "2026-10-12", "2026-10-12"))
        assertTrue(OreReminders.canDeliver(true, 10, "2026-10-12", "2026-10-11"))
    }
}
