package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import me.ash.reader.ui.ext.PreferencesKey
import me.ash.reader.ui.ext.dataStore

data class AiConfigPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val provider: String,
)

data class AiConfigPresetState(
    val presets: List<AiConfigPreset>,
    val currentPresetId: String,
)

private val gson = Gson()
private val presetListType = object : TypeToken<List<AiConfigPreset>>() {}.type

fun AiConfigPreset.summary(): String {
    val modelPart = model.ifBlank { "No model" }
    val hostPart = runCatching { java.net.URI(baseUrl).host.orEmpty() }.getOrDefault(baseUrl).ifBlank { baseUrl }
    return listOf(provider, modelPart, hostPart).joinToString(" · ")
}

fun detectAiProvider(baseUrl: String): String {
    val normalized = AiBaseUrlPreference.normalize(baseUrl)
    return when {
        normalized.contains("api.deepseek.com", ignoreCase = true) -> "DeepSeek"
        normalized.contains("api.openai.com", ignoreCase = true) -> "OpenAI"
        else -> "Custom"
    }
}

fun buildAiConfigPreset(
    id: String = UUID.randomUUID().toString(),
    name: String,
    baseUrl: String,
    apiKey: String,
    model: String,
): AiConfigPreset =
    AiConfigPreset(
        id = id,
        name = name.trim().ifBlank { DEFAULT_AI_PRESET_NAME },
        baseUrl = AiBaseUrlPreference.normalize(baseUrl),
        apiKey = apiKey.trim(),
        model = model.trim(),
        provider = detectAiProvider(baseUrl),
    )

fun Preferences.readAiConfigPresetState(): AiConfigPresetState? {
    val presetJson =
        (PreferencesKey.keys[PreferencesKey.aiConfigPresets] as? PreferencesKey.StringKey)
            ?.let { this[it.key] }
            .orEmpty()
    val currentPresetId =
        (PreferencesKey.keys[PreferencesKey.aiCurrentPresetId] as? PreferencesKey.StringKey)
            ?.let { this[it.key] }
            .orEmpty()
    if (presetJson.isBlank()) return null
    val presets = runCatching { gson.fromJson<List<AiConfigPreset>>(presetJson, presetListType) }.getOrDefault(emptyList())
        .map {
            buildAiConfigPreset(
                id = it.id.ifBlank { UUID.randomUUID().toString() },
                name = it.name,
                baseUrl = it.baseUrl,
                apiKey = it.apiKey,
                model = it.model,
            )
        }
        .distinctBy { it.id }
    if (presets.isEmpty()) return null
    val resolvedCurrentPresetId = currentPresetId.takeIf { id -> presets.any { it.id == id } } ?: presets.first().id
    return AiConfigPresetState(presets = presets, currentPresetId = resolvedCurrentPresetId)
}

fun Preferences.readLegacyAiConfigPresetState(): AiConfigPresetState? {
    val legacyBaseUrl = AiBaseUrlPreference.fromPreferences(this).value
    val legacyApiKey = AiApiKeyPreference.fromPreferences(this).value
    val legacyModel = AiModelPreference.fromPreferences(this).value
    if (legacyApiKey.isBlank() && legacyModel.isBlank() && legacyBaseUrl == AiBaseUrlPreference.DEFAULT_BASE_URL) {
        return null
    }
    val preset =
        buildAiConfigPreset(
            name = DEFAULT_AI_PRESET_NAME,
            baseUrl = legacyBaseUrl,
            apiKey = legacyApiKey,
            model = legacyModel,
        )
    return AiConfigPresetState(presets = listOf(preset), currentPresetId = preset.id)
}

suspend fun Context.writeAiConfigPresetState(state: AiConfigPresetState) {
    dataStore.edit { preferences ->
        val presetKey = (PreferencesKey.keys[PreferencesKey.aiConfigPresets] as PreferencesKey.StringKey).key
        val currentKey = (PreferencesKey.keys[PreferencesKey.aiCurrentPresetId] as PreferencesKey.StringKey).key
        preferences[presetKey] = gson.toJson(state.presets)
        preferences[currentKey] = state.currentPresetId
    }
}

suspend fun Context.updateAiConfigPresetState(
    fallback: AiConfigPresetState? = null,
    transform: (AiConfigPresetState) -> AiConfigPresetState,
) {
    dataStore.edit { preferences ->
        val presetKey = (PreferencesKey.keys[PreferencesKey.aiConfigPresets] as PreferencesKey.StringKey).key
        val currentKey = (PreferencesKey.keys[PreferencesKey.aiCurrentPresetId] as PreferencesKey.StringKey).key
        val currentState = preferences.readAiConfigPresetState() ?: fallback ?: AiConfigPresetState(emptyList(), "")
        val nextState = transform(currentState)
        if (nextState.presets.isEmpty()) {
            preferences.remove(presetKey)
            preferences.remove(currentKey)
        } else {
            val resolvedCurrentId =
                nextState.currentPresetId.takeIf { id -> nextState.presets.any { it.id == id } }
                    ?: nextState.presets.first().id
            preferences[presetKey] = gson.toJson(nextState.presets)
            preferences[currentKey] = resolvedCurrentId
        }
    }
}

const val DEFAULT_AI_PRESET_NAME = "Default"
