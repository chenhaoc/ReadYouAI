package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.aiBaseUrl
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalAiBaseUrl = compositionLocalOf { AiBaseUrlPreference.default }

data class AiBaseUrlPreference(val value: String) : Preference() {

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(aiBaseUrl, normalize(value))
        }
    }

    fun toDesc(context: Context): String = normalize(value)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1/"
        val default = AiBaseUrlPreference(DEFAULT_BASE_URL)

        fun normalize(value: String): String =
            value.trim().trimEnd('/').takeIf { it.isNotBlank() }?.let { "$it/" }
                ?: DEFAULT_BASE_URL

        fun fromPreferences(preferences: Preferences): AiBaseUrlPreference {
            return AiBaseUrlPreference(
                normalize(
                    preferences[DataStoreKey.keys[aiBaseUrl]?.key as Preferences.Key<String>]
                        ?: default.value
                )
            )
        }
    }
}
