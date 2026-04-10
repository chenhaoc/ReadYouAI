package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.aiAutoSummary
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalAiAutoSummary = compositionLocalOf { AiAutoSummaryPreference.default }

class AiAutoSummaryPreference(val value: Boolean) : Preference() {
    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(aiAutoSummary, value)
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) =
        AiAutoSummaryPreference(!value).put(context, scope)

    companion object {
        val default = AiAutoSummaryPreference(false)

        fun fromPreferences(preferences: Preferences): AiAutoSummaryPreference {
            return AiAutoSummaryPreference(
                preferences[DataStoreKey.keys[aiAutoSummary]?.key as Preferences.Key<Boolean>]
                    ?: return default
            )
        }
    }
}
