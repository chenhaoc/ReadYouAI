package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalCommuteBriefDuration =
    compositionLocalOf<CommuteBriefDurationPreference> { CommuteBriefDurationPreference.default }
val LocalCommuteBriefMarkReadOnComplete =
    compositionLocalOf<CommuteBriefMarkReadOnCompletePreference> { CommuteBriefMarkReadOnCompletePreference.default }

sealed class CommuteBriefDurationPreference(val minutes: Int) : Preference() {
    data object FifteenMinutes : CommuteBriefDurationPreference(15)
    data object ThirtyMinutes : CommuteBriefDurationPreference(30)
    data object FortyFiveMinutes : CommuteBriefDurationPreference(45)
    data object SixtyMinutes : CommuteBriefDurationPreference(60)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch { context.dataStore.put(DataStoreKey.commuteBriefDurationMinutes, minutes) }
    }

    companion object {
        val default = ThirtyMinutes
        val values = listOf(FifteenMinutes, ThirtyMinutes, FortyFiveMinutes, SixtyMinutes)

        fun fromMinutes(minutes: Int): CommuteBriefDurationPreference =
            values.firstOrNull { it.minutes == minutes } ?: default

        fun fromPreferences(preferences: Preferences): CommuteBriefDurationPreference =
            fromMinutes(
                preferences[DataStoreKey.keys[DataStoreKey.commuteBriefDurationMinutes]?.key as Preferences.Key<Int>]
                    ?: default.minutes
            )
    }
}

sealed class CommuteBriefMarkReadOnCompletePreference(val value: Boolean) : Preference() {
    data object On : CommuteBriefMarkReadOnCompletePreference(true)
    data object Off : CommuteBriefMarkReadOnCompletePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch { context.dataStore.put(DataStoreKey.commuteBriefMarkReadOnComplete, value) }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(DataStoreKey.commuteBriefMarkReadOnComplete, !value)
    }

    companion object {
        val default = Off

        fun fromPreferences(preferences: Preferences): CommuteBriefMarkReadOnCompletePreference =
            when (preferences[DataStoreKey.keys[DataStoreKey.commuteBriefMarkReadOnComplete]?.key as Preferences.Key<Boolean>]) {
                true -> On
                false -> Off
                else -> default
            }
    }
}
