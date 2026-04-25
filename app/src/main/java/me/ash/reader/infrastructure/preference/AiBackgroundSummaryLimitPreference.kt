package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.aiBackgroundSummaryBackfillOnSync
import me.ash.reader.ui.ext.DataStoreKey.Companion.aiBackgroundSummaryLimit
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalAiBackgroundSummaryLimit = compositionLocalOf { AiBackgroundSummaryLimitPreference.default }
val LocalAiBackgroundSummaryBackfillOnSync =
    compositionLocalOf { AiBackgroundSummaryBackfillOnSyncPreference.default }

sealed class AiBackgroundSummaryLimitPreference(val value: Int, val limit: Int?) : Preference() {
    data object Ten : AiBackgroundSummaryLimitPreference(10, 10)
    data object TwentyFive : AiBackgroundSummaryLimitPreference(25, 25)
    data object Fifty : AiBackgroundSummaryLimitPreference(50, 50)
    data object OneHundred : AiBackgroundSummaryLimitPreference(100, 100)
    data object Unlimited : AiBackgroundSummaryLimitPreference(-1, null)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch { context.dataStore.put(aiBackgroundSummaryLimit, value) }
    }

    companion object {
        val default: AiBackgroundSummaryLimitPreference
            get() = TwentyFive
        val values: List<AiBackgroundSummaryLimitPreference>
            get() = listOf(Ten, TwentyFive, Fifty, OneHundred, Unlimited)

        fun fromValue(value: Int): AiBackgroundSummaryLimitPreference =
            when (value) {
                Ten.value -> Ten
                TwentyFive.value -> TwentyFive
                Fifty.value -> Fifty
                OneHundred.value -> OneHundred
                Unlimited.value -> Unlimited
                else -> default
            }

        fun fromPreferences(preferences: Preferences): AiBackgroundSummaryLimitPreference {
            return fromValue(
                preferences[DataStoreKey.keys[aiBackgroundSummaryLimit]?.key as Preferences.Key<Int>]
                    ?: return default
            )
        }
    }
}

class AiBackgroundSummaryBackfillOnSyncPreference(val value: Boolean) : Preference() {
    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch { context.dataStore.put(aiBackgroundSummaryBackfillOnSync, value) }
    }

    fun toggle(context: Context, scope: CoroutineScope) =
        AiBackgroundSummaryBackfillOnSyncPreference(!value).put(context, scope)

    companion object {
        val default = AiBackgroundSummaryBackfillOnSyncPreference(false)

        fun fromPreferences(preferences: Preferences): AiBackgroundSummaryBackfillOnSyncPreference {
            return AiBackgroundSummaryBackfillOnSyncPreference(
                preferences[DataStoreKey.keys[aiBackgroundSummaryBackfillOnSync]?.key as Preferences.Key<Boolean>]
                    ?: return default
            )
        }
    }
}
