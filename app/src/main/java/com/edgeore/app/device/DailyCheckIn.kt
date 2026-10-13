package com.edgeore.app.device

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Local daily check-in: up to [SLOTS_PER_DAY] slots per UTC day, each an active window of [SLOT_MS]. Rewards are a
 * streak and points kept on this phone only: no token reward, nothing claimable, nothing sent anywhere.
 * This is EdgeORE's own app policy, not an ORE protocol rule; the device clock is trusted (no server exists yet).
 */
data class CheckInState(
    val day: String = "",
    val slotStarts: List<Long> = emptyList(),
    val streakDays: Int = 0,
    val lastDay: String? = null,
    val points: Int = 0,
) {
    fun toJson(): String =
        JSONObject()
            .put("day", day)
            .put("slots", JSONArray(slotStarts))
            .put("streak", streakDays)
            .put(
                "lastDay",
                lastDay ?: JSONObject.NULL,
            ).put("points", points)
            .toString()

    companion object {
        fun fromJson(s: String?): CheckInState =
            runCatching {
                val j = JSONObject(s!!)
                val a = j.getJSONArray("slots")
                CheckInState(
                    j.getString("day"),
                    List(a.length()) { a.getLong(it) },
                    j.getInt("streak"),
                    j.optString("lastDay").takeIf {
                        it.isNotEmpty() &&
                            it != "null"
                    },
                    j.getInt("points"),
                )
            }.getOrDefault(CheckInState())
    }
}

object DailyCheckIn {
    const val SLOTS_PER_DAY = 5
    const val SLOT_MS = 60 * 60 * 1000L
    const val REWARD_LABEL = "Streak and points saved on your phone"

    fun utcDay(ms: Long): String =
        Instant
            .ofEpochMilli(ms)
            .atOffset(ZoneOffset.UTC)
            .toLocalDate()
            .toString()

    fun nextResetMs(ms: Long): Long =
        LocalDate
            .parse(utcDay(ms))
            .plusDays(1)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()

    /** Rolls the state into [now]'s UTC day (yesterday's slots no longer count). */
    fun normalize(
        s: CheckInState,
        now: Long,
    ): CheckInState =
        if (s.day ==
            utcDay(now)
        ) {
            s
        } else {
            s.copy(day = utcDay(now), slotStarts = emptyList())
        }

    fun used(
        s: CheckInState,
        now: Long,
    ) = normalize(s, now).slotStarts.size

    fun activeUntil(
        s: CheckInState,
        now: Long,
    ): Long? =
        normalize(s, now)
            .slotStarts
            .lastOrNull()
            ?.let { it + SLOT_MS }
            ?.takeIf { it > now }

    fun available(
        s: CheckInState,
        now: Long,
    ): Boolean = activeUntil(s, now) == null && used(s, now) < SLOTS_PER_DAY

    sealed interface Result {
        data class Started(
            val state: CheckInState,
        ) : Result

        data class Refused(
            val reason: String,
        ) : Result
    }

    fun checkIn(
        s0: CheckInState,
        now: Long,
    ): Result {
        val s = normalize(s0, now)
        activeUntil(s, now)?.let { return Result.Refused("A slot is already active") }
        if (s.slotStarts.size >= SLOTS_PER_DAY) return Result.Refused("All $SLOTS_PER_DAY slots used today")
        val today = s.day
        val yesterday = LocalDate.parse(today).minusDays(1).toString()
        val streak =
            when (s.lastDay) {
                today -> s.streakDays
                yesterday -> s.streakDays + 1
                else -> 1
            }
        return Result.Started(s.copy(slotStarts = s.slotStarts + now, streakDays = streak, lastDay = today, points = s.points + 1))
    }
}
