package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.aiChatPrompt
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put
import me.ash.reader.ui.page.home.reading.resolveAiChatPrompt

val LocalAiChatPrompt = compositionLocalOf { AiChatPromptPreference.default }

data class AiChatPromptPreference(val value: String) : Preference() {

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(aiChatPrompt, value)
        }
    }

    fun toDesc(context: Context): String =
        resolveAiChatPrompt(
            value.ifEmpty { context.getString(R.string.ai_chat_prompt_default) }
        ).lineSequence().first().trim()

    companion object {
        val default = AiChatPromptPreference("")

        fun fromPreferences(preferences: Preferences): AiChatPromptPreference {
            return AiChatPromptPreference(
                preferences[DataStoreKey.keys[aiChatPrompt]?.key as Preferences.Key<String>] ?: default.value
            )
        }
    }
}
