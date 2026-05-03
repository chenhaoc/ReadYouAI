package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.aiCommuteBriefRecommendationPrompt
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put
import me.ash.reader.ui.page.home.reading.resolveAiCommuteBriefRecommendationPrompt

val LocalAiCommuteBriefRecommendationPrompt =
    compositionLocalOf { AiCommuteBriefRecommendationPromptPreference.default }

data class AiCommuteBriefRecommendationPromptPreference(val value: String) : Preference() {

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(aiCommuteBriefRecommendationPrompt, value)
        }
    }

    fun toDesc(context: Context): String =
        resolveAiCommuteBriefRecommendationPrompt(
            value.ifEmpty { context.getString(R.string.ai_commute_brief_recommendation_prompt_default) }
        ).lineSequence().first().trim()

    companion object {
        val default = AiCommuteBriefRecommendationPromptPreference("")

        fun fromPreferences(preferences: Preferences): AiCommuteBriefRecommendationPromptPreference {
            return AiCommuteBriefRecommendationPromptPreference(
                preferences[DataStoreKey.keys[aiCommuteBriefRecommendationPrompt]?.key as Preferences.Key<String>]
                    ?: default.value
            )
        }
    }
}
