package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.aiTranslationPrompt
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put
import me.ash.reader.ui.page.home.reading.resolveAiTranslationPrompt

val LocalAiTranslationPrompt = compositionLocalOf { AiTranslationPromptPreference.default }

data class AiTranslationPromptPreference(val value: String) : Preference() {

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(aiTranslationPrompt, value)
        }
    }

    fun toDesc(context: Context): String =
        resolveAiTranslationPrompt(
            value.ifEmpty { context.getString(R.string.ai_translation_prompt_default) }
        ).lineSequence().first().trim()

    companion object {
        val default = AiTranslationPromptPreference("")

        fun fromPreferences(preferences: Preferences): AiTranslationPromptPreference {
            return AiTranslationPromptPreference(
                preferences[DataStoreKey.keys[aiTranslationPrompt]?.key as Preferences.Key<String>]
                    ?: default.value
            )
        }
    }
}
