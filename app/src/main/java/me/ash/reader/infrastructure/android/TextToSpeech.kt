package me.ash.reader.infrastructure.android

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.html.VideoNoiseCleaner
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    @ApplicationScope
    private val coroutineScope: CoroutineScope,
) {
    private class TtsHandle(
        val engine: TextToSpeech,
        val initialization: CompletableDeferred<Boolean>,
    )

    private val _stateFlow = MutableStateFlow<State>(State.Idle)
    val stateFlow = _stateFlow.asStateFlow()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private val engineLock = Any()
    private var playbackGeneration: Long = 0L

    var state
        get() = stateFlow.value
        private set(value) {
            _stateFlow.value = value
        }

    private val speechAudioAttributes: AudioAttributes by lazy(LazyThreadSafetyMode.NONE) {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
    @Volatile
    private var ttsHandle: TtsHandle = createTtsHandle()

    private fun createTtsHandle(): TtsHandle {
        val initialization = CompletableDeferred<Boolean>()
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context, TextToSpeech.OnInitListener {
            when (it) {
                TextToSpeech.SUCCESS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        engine?.setAudioAttributes(speechAudioAttributes)
                    }
                    if (!initialization.isCompleted) {
                        initialization.complete(true)
                    }
                }
                else -> {
                    Timber.e("TextToSpeech initialization failed $it")
                    if (!initialization.isCompleted) {
                        initialization.complete(false)
                    }
                }
            }
        })
        return TtsHandle(
            engine = checkNotNull(engine),
            initialization = initialization,
        )
    }

    sealed interface State {
        object Idle : State
        object Preparing : State
        class Reading(val current: Int, val total: Int) : State {
            val progress: Float
                get() = current.toFloat() / total
        }

        object Error : State
    }

    sealed interface Event {
        data object Completed : Event

        data class Progress(val current: Int, val total: Int) : Event

        data class Failed(val utteranceId: String?) : Event
    }


    fun readHtml(htmlContent: String, startSegmentIndex: Int = 0) {
        coroutineScope.launch {
            readText(
                text = htmlToPlainText(htmlContent),
                startSegmentIndex = startSegmentIndex,
            )
        }
    }

    private suspend fun readText(text: String, startSegmentIndex: Int = 0) {
        if (state != State.Idle) {
            stop()
        }

        val generation = nextPlaybackGeneration()
        state = State.Preparing
        val handle = ensureTtsReady() ?: return
        val tts = handle.engine

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tts.language =
                context.detectLocaleFromText(text.take(text.lastIndex.coerceAtMost(500)))
                    .firstOrNull()?.locale
        }

        val textSegments = splitSpeakableSegments(text)
        val total = textSegments.size
        if (total == 0) {
            state = State.Idle
            return
        }
        val actualStartIndex = startSegmentIndex.coerceIn(0, total - 1)
        if (!isCurrentPlayback(generation)) return
        state = State.Reading(actualStartIndex, total)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (!isCurrentPlayback(generation)) return
                val current = utteranceId?.toIntOrNull() ?: 0
                state = State.Reading(current, total)
                _events.tryEmit(Event.Progress(current = current, total = total))
            }

            override fun onDone(utteranceId: String?) {
                if (!isCurrentPlayback(generation)) return
                val index = utteranceId?.toIntOrNull() ?: 0
                val cur = state
                if (cur is State.Reading && index >= cur.total) {
                    state = State.Idle
                    _events.tryEmit(Event.Completed)
                }
            }

            override fun onError(utteranceId: String?) {
                if (!isCurrentPlayback(generation)) return
                reportPlaybackFailure(utteranceId, handle)
            }
        })

        textSegments.drop(actualStartIndex).forEachIndexed { offset, segment ->
            if (!isCurrentPlayback(generation)) return
            val actualIndex = actualStartIndex + offset
            val result = tts.speak(segment, TextToSpeech.QUEUE_ADD, null, (actualIndex + 1).toString())
            if (result != TextToSpeech.SUCCESS) {
                Timber.w("TextToSpeech speak failed result=%s index=%s", result, actualIndex)
                if (!isCurrentPlayback(generation)) return
                reportPlaybackFailure((actualIndex + 1).toString(), handle)
                return
            }
        }
    }

    fun stop() {
        nextPlaybackGeneration()
        ttsHandle.engine.stop()
        state = State.Idle
    }

    private fun nextPlaybackGeneration(): Long =
        synchronized(engineLock) {
            playbackGeneration += 1
            playbackGeneration
        }

    private fun isCurrentPlayback(generation: Long): Boolean =
        synchronized(engineLock) { playbackGeneration == generation }

    private suspend fun ensureTtsReady(): TtsHandle? {
        repeat(2) {
            val currentHandle = ttsHandle
            val initialized = currentHandle.initialization.await()
            if (initialized) {
                if (ttsHandle === currentHandle) {
                    return currentHandle
                }
            } else {
                Timber.w("TextToSpeech not ready, rebuilding engine attempt=%s", it + 1)
                rebuildTtsEngineIfCurrent(currentHandle)
            }
        }
        state = State.Error
        _events.tryEmit(Event.Failed(null))
        return null
    }

    private fun reportPlaybackFailure(
        utteranceId: String?,
        handle: TtsHandle,
    ) {
        state = State.Error
        _events.tryEmit(Event.Failed(utteranceId))
        coroutineScope.launch {
            rebuildTtsEngineIfCurrent(handle)
        }
    }

    private fun rebuildTtsEngineIfCurrent(handle: TtsHandle) {
        val staleHandle =
            synchronized(engineLock) {
                if (ttsHandle !== handle) return
                ttsHandle = createTtsHandle()
                handle
            }
        staleHandle.engine.shutdown()
    }
}

internal fun htmlToPlainText(htmlContent: String): String =
    Html.fromHtml(
        VideoNoiseCleaner.cleanHtml(htmlContent),
        Html.FROM_HTML_MODE_LEGACY,
    ).toString()

internal fun splitSpeakableSegments(text: String): List<String> =
    text.split("\n")
        .map(String::trim)
        .filter(String::isNotBlank)
        .flatMap(::splitLongSpeakableSegment)

private fun splitLongSpeakableSegment(
    segment: String,
    targetChars: Int = 180,
    minChunkChars: Int = 60,
): List<String> {
    if (segment.length <= targetChars) return listOf(segment)

    val chunks = mutableListOf<String>()
    var start = 0
    while (start < segment.length) {
        val remaining = segment.length - start
        if (remaining <= targetChars) {
            chunks += segment.substring(start)
            break
        }

        val idealEnd = (start + targetChars).coerceAtMost(segment.length)
        val searchStart = (start + minChunkChars).coerceAtMost(idealEnd)
        val splitIndex =
            findChunkBoundary(
                segment = segment,
                start = searchStart,
                endInclusive = idealEnd - 1,
            ) ?: idealEnd

        chunks += segment.substring(start, splitIndex)
        start = splitIndex
    }
    return chunks.filter(String::isNotBlank)
}

private fun findChunkBoundary(
    segment: String,
    start: Int,
    endInclusive: Int,
): Int? {
    for (index in endInclusive downTo start) {
        if (segment[index].isChunkBoundaryChar()) {
            return index + 1
        }
    }
    return null
}

private fun Char.isChunkBoundaryChar(): Boolean =
    this == '。' ||
        this == '！' ||
        this == '？' ||
        this == '；' ||
        this == '，' ||
        this == '、' ||
        this == '.' ||
        this == '!' ||
        this == '?' ||
        this == ';' ||
        this == ',' ||
        this.isWhitespace()

internal fun htmlSegmentCharCounts(htmlContent: String): List<Int> =
    splitSpeakableSegments(htmlToPlainText(htmlContent)).map(String::length)

@RequiresApi(Build.VERSION_CODES.Q)
private fun Context.detectLocaleFromText(
    text: CharSequence,
    minConfidence: Float = 80.0f,
): Sequence<LocaleWithConfidence> {
    val textClassificationManager =
        getSystemService<TextClassificationManager>() ?: return emptySequence()
    val textClassifier = textClassificationManager.textClassifier

    val textRequest = TextLanguage.Request.Builder(text).build()
    val detectedLanguage = textClassifier.detectLanguage(textRequest)

    return sequence {
        for (i in 0 until detectedLanguage.localeHypothesisCount) {
            val localeDetected = detectedLanguage.getLocale(i)
            val confidence = detectedLanguage.getConfidenceScore(localeDetected) * 100.0f
            if (confidence >= minConfidence) {
                yield(
                    LocaleWithConfidence(
                        locale = localeDetected.toLocale(),
                        confidence = confidence,
                    ),
                )
            }
        }
    }
}

data class LocaleWithConfidence(
    val locale: Locale,
    val confidence: Float,
)
