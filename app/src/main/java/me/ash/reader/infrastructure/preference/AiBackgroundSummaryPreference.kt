package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.aiBackgroundSummary
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalAiBackgroundSummary = compositionLocalOf { AiBackgroundSummaryPreference.default }

class AiBackgroundSummaryPreference(val value: Boolean) : Preference() {
    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(aiBackgroundSummary, value)
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) =
        AiBackgroundSummaryPreference(!value).put(context, scope)

    companion object {
        val default = AiBackgroundSummaryPreference(false)

        fun fromPreferences(preferences: Preferences): AiBackgroundSummaryPreference {
            return AiBackgroundSummaryPreference(
                preferences[DataStoreKey.keys[aiBackgroundSummary]?.key as Preferences.Key<Boolean>]
                    ?: return default
            )
        }
    }
}
