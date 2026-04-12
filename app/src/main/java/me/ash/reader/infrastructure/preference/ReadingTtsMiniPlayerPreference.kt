package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.readingTtsMiniPlayer
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalReadingTtsMiniPlayer =
    compositionLocalOf<ReadingTtsMiniPlayerPreference> { ReadingTtsMiniPlayerPreference.default }

sealed class ReadingTtsMiniPlayerPreference(val value: Boolean) : Preference() {
    data object ON : ReadingTtsMiniPlayerPreference(true)
    data object OFF : ReadingTtsMiniPlayerPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(readingTtsMiniPlayer, value)
        }
    }

    companion object {
        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[readingTtsMiniPlayer]?.key as Preferences.Key<Boolean>]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingTtsMiniPlayerPreference.not(): ReadingTtsMiniPlayerPreference =
    when (value) {
        true -> ReadingTtsMiniPlayerPreference.OFF
        false -> ReadingTtsMiniPlayerPreference.ON
    }
