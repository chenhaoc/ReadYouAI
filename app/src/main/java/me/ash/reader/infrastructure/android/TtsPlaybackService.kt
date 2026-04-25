package me.ash.reader.infrastructure.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueController
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState
import me.ash.reader.infrastructure.android.ttsqueue.msToSegmentIndex
import me.ash.reader.infrastructure.android.ttsqueue.segmentCharCountsToDurationEstimate
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.ui.page.common.NotificationGroupName
import kotlin.math.roundToInt

@AndroidEntryPoint
class TtsPlaybackService : Service() {

    @Inject
    lateinit var ttsQueueController: TtsQueueController

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var wakeLock: PowerManager.WakeLock? = null
    private var stateObserverJob: Job? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var mediaPlaybackAnchor: SilentMediaPlaybackAnchor? = null
    private val notificationLargeIcon: Bitmap by lazy(LazyThreadSafetyMode.NONE) {
        packageManager.getApplicationIcon(packageName).toNotificationLargeIcon(resources)
    }
    private val audioManager: AudioManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val speechAudioAttributes: AudioAttributes by lazy(LazyThreadSafetyMode.NONE) {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                if (ttsQueueController.state.value.isPlaying()) {
                    ttsQueueController.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> hasAudioFocus = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        updateMediaSession(ttsQueueController.state.value)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            ensureMediaButtonSessionReady()
            handleMediaButtonIntent(intent)
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PAUSE -> {
                ttsQueueController.pause()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                ttsQueueController.stop()
                return START_NOT_STICKY
            }
            ACTION_SKIP_NEXT -> {
                ttsQueueController.skipToNext()
                return START_NOT_STICKY
            }
            ACTION_SKIP_PREVIOUS -> {
                ttsQueueController.skipToPrevious()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                ttsQueueController.resumeCurrent()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_STARRED -> {
                ttsQueueController.toggleCurrentStarred()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification(ttsQueueController.state.value))
        updateWakeLock(ttsQueueController.state.value)
        updateAudioFocus(ttsQueueController.state.value)
        observeState()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        releaseMediaPlaybackAnchor()
        releaseWakeLock()
        abandonAudioFocusIfNeeded()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NotificationGroupName.TTS_PLAYBACK,
            getString(R.string.tts_playback_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "ReadYouTts").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            @Suppress("DEPRECATION")
            setPlaybackToLocal(AudioManager.STREAM_MUSIC)
            setMediaButtonReceiver(buildMediaButtonPendingIntent())
            setSessionActivity(buildContentPendingIntent())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (requestAudioFocusIfNeeded()) {
                        ttsQueueController.resumeCurrent()
                    }
                }

                override fun onPause() {
                    ttsQueueController.pause()
                }

                override fun onSkipToNext() {
                    ttsQueueController.skipToNext()
                }

                override fun onSkipToPrevious() {
                    ttsQueueController.skipToPrevious()
                }

                override fun onStop() {
                    ttsQueueController.pause()
                }

                override fun onSeekTo(pos: Long) {
                    val segmentCharCounts = ttsQueueController.state.value.currentSegmentCharCounts
                    val targetSegment = msToSegmentIndex(pos, segmentCharCounts)
                    ttsQueueController.seekCurrent(targetSegment)
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    if (action == ACTION_TOGGLE_STARRED) {
                        ttsQueueController.toggleCurrentStarred()
                    }
                }
            })
            isActive = ttsQueueController.state.value.hasActiveMediaSession()
        }
    }

    private fun observeState() {
        stateObserverJob?.cancel()
        stateObserverJob = applicationScope.launch {
            ttsQueueController.state.collectLatest { state ->
                updateMediaSession(state)
                updateWakeLock(state)
                updateAudioFocus(state)
                updateMediaPlaybackAnchor(ttsQueueController.state.value)
                when (state.playbackState) {
                    TtsQueuePlaybackState.Reading,
                    TtsQueuePlaybackState.Preparing,
                    TtsQueuePlaybackState.Error -> {
                        val manager = getSystemService(NotificationManager::class.java)
                        manager.notify(NOTIFICATION_ID, buildNotification(state))
                    }
                    TtsQueuePlaybackState.Idle -> {
                        if (state.currentItem != null) {
                            val manager = getSystemService(NotificationManager::class.java)
                            manager.notify(NOTIFICATION_ID, buildNotification(state))
                        } else {
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    private fun updateMediaSession(state: TtsQueueState) {
        val session = mediaSession ?: return
        val currentItem = state.currentItem
        val isPlaying = state.isPlaying()
        session.isActive = state.hasActiveMediaSession()

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                currentItem?.title ?: getString(R.string.tts_playing),
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                currentItem?.feedName.orEmpty(),
            )

        val durationEstimate =
            segmentCharCountsToDurationEstimate(
                currentSegmentIndex = state.currentSegmentIndex,
                segmentCharCounts = state.currentSegmentCharCounts,
            )
        metadataBuilder.putLong(
            MediaMetadataCompat.METADATA_KEY_DURATION,
            durationEstimate?.totalMs?.coerceAtLeast(1L) ?: 1L,
        )
        session.setMetadata(metadataBuilder.build())

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(state.supportedMediaSessionActions())
            .setState(
                state.toMediaSessionPlaybackState(),
                durationEstimate?.currentMs ?: 0L,
                if (isPlaying) 1f else 0f,
                SystemClock.elapsedRealtime(),
            )
        if (currentItem != null) {
            stateBuilder.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_TOGGLE_STARRED,
                    getString(
                        if (state.currentItemStarred) {
                            R.string.mark_as_unstar
                        } else {
                            R.string.mark_as_starred
                        }
                    ),
                    if (state.currentItemStarred) R.drawable.ic_star else R.drawable.ic_star_outline,
                ).build()
            )
        }
        session.setPlaybackState(stateBuilder.build())
    }

    private fun buildNotification(state: TtsQueueState): android.app.Notification {
        val currentItem = state.currentItem
        val title = currentItem?.title ?: getString(R.string.tts_playing)
        val subtitle = currentItem?.feedName.orEmpty()
        val isPlaying = state.isPlaying()
        val contentIntent = buildContentPendingIntent()

        val currentIndex = state.currentIndex
        val totalItems = state.items.size
        val subText = if (currentIndex != null && totalItems > 0) {
            "${currentIndex + 1} / $totalItems"
        } else {
            null
        }

        val builder = NotificationCompat.Builder(this, NotificationGroupName.TTS_PLAYBACK)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(notificationLargeIcon)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (subText != null) {
            builder.setSubText(subText)
        }

        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_skip_previous,
                getString(R.string.tts_action_previous),
                buildActionPendingIntent(ACTION_SKIP_PREVIOUS, REQUEST_CODE_PREVIOUS),
            ).build()
        )

        if (isPlaying) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_pause,
                    getString(R.string.tts_action_pause),
                    buildActionPendingIntent(ACTION_PAUSE, REQUEST_CODE_TOGGLE),
                ).build()
            )
        } else {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_play,
                    getString(R.string.tts_action_play),
                    buildActionPendingIntent(ACTION_RESUME, REQUEST_CODE_TOGGLE),
                ).build()
            )
        }

        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_skip_next,
                getString(R.string.tts_action_next),
                buildActionPendingIntent(ACTION_SKIP_NEXT, REQUEST_CODE_NEXT),
            ).build()
        )

        if (currentItem != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    if (state.currentItemStarred) R.drawable.ic_star else R.drawable.ic_star_outline,
                    getString(
                        if (state.currentItemStarred) {
                            R.string.mark_as_unstar
                        } else {
                            R.string.mark_as_starred
                        }
                    ),
                    buildActionPendingIntent(ACTION_TOGGLE_STARRED, REQUEST_CODE_TOGGLE_STARRED),
                ).build()
            )
        }

        builder.setStyle(
            MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession?.sessionToken)
        )

        return builder.build()
    }

    private fun buildContentPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildMediaButtonPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_MEDIA_BUTTON,
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = ComponentName(this@TtsPlaybackService, MediaButtonReceiver::class.java)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TtsPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureMediaButtonSessionReady() {
        if (stateObserverJob != null) return
        startForeground(NOTIFICATION_ID, buildNotification(ttsQueueController.state.value))
        updateWakeLock(ttsQueueController.state.value)
        updateAudioFocus(ttsQueueController.state.value)
        updateMediaPlaybackAnchor(ttsQueueController.state.value)
        observeState()
    }

    private fun handleMediaButtonIntent(intent: Intent) {
        val mediaButtonIntent = Intent(intent)
        if (!ttsQueueController.isRestoreCompleted) {
            applicationScope.launch {
                ttsQueueController.awaitRestore()
                withContext(Dispatchers.Main.immediate) {
                    val restoredState = ttsQueueController.state.value
                    updateMediaSession(restoredState)
                    updateWakeLock(restoredState)
                    updateAudioFocus(restoredState)
                    dispatchMediaButtonIntent(mediaButtonIntent)
                }
            }
            return
        }
        dispatchMediaButtonIntent(mediaButtonIntent)
    }

    private fun dispatchMediaButtonIntent(intent: Intent) {
        updateMediaSession(ttsQueueController.state.value)
        mediaSession?.let { MediaButtonReceiver.handleIntent(it, intent) }
    }

    private fun updateAudioFocus(state: TtsQueueState) {
        if (state.isPlaying()) {
            if (!requestAudioFocusIfNeeded() && ttsQueueController.state.value.isPlaying()) {
                ttsQueueController.pause()
            }
        } else {
            abandonAudioFocusIfNeeded()
        }
    }

    private fun updateMediaPlaybackAnchor(state: TtsQueueState) {
        if (state.isPlaying()) {
            val anchor = mediaPlaybackAnchor ?: SilentMediaPlaybackAnchor(speechAudioAttributes).also {
                mediaPlaybackAnchor = it
            }
            anchor.start()
        } else {
            mediaPlaybackAnchor?.pause()
        }
    }

    private fun releaseMediaPlaybackAnchor() {
        mediaPlaybackAnchor?.release()
        mediaPlaybackAnchor = null
    }

    private fun requestAudioFocusIfNeeded(): Boolean {
        if (hasAudioFocus) return true
        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request =
                    audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(speechAudioAttributes)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build()
                        .also { audioFocusRequest = it }
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocusIfNeeded() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    private fun updateWakeLock(state: TtsQueueState) {
        val shouldHoldWakeLock =
            state.playbackState == TtsQueuePlaybackState.Reading ||
                state.playbackState == TtsQueuePlaybackState.Preparing
        if (shouldHoldWakeLock) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        val existingWakeLock = wakeLock
        if (existingWakeLock?.isHeld == true) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val newWakeLock =
            existingWakeLock ?: powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ReadYou::TtsPlayback",
            ).apply {
                setReferenceCounted(false)
            }
        wakeLock = newWakeLock
        newWakeLock.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 20001
        private const val ACTION_PAUSE = "me.ash.reader.TTS_PAUSE"
        private const val ACTION_STOP = "me.ash.reader.TTS_STOP"
        private const val ACTION_RESUME = "me.ash.reader.TTS_RESUME"
        private const val ACTION_SKIP_NEXT = "me.ash.reader.TTS_SKIP_NEXT"
        private const val ACTION_SKIP_PREVIOUS = "me.ash.reader.TTS_SKIP_PREVIOUS"
        private const val ACTION_TOGGLE_STARRED = "me.ash.reader.TTS_TOGGLE_STARRED"
        private const val REQUEST_CODE_PREVIOUS = 1001
        private const val REQUEST_CODE_TOGGLE = 1002
        private const val REQUEST_CODE_NEXT = 1003
        private const val REQUEST_CODE_MEDIA_BUTTON = 1004
        private const val REQUEST_CODE_TOGGLE_STARRED = 1005

        fun startService(context: Context) {
            val intent = Intent(context, TtsPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TtsPlaybackService::class.java)
            context.stopService(intent)
        }
    }
}

private class SilentMediaPlaybackAnchor(
    private val audioAttributes: AudioAttributes,
) {
    private var audioTrack: AudioTrack? = null

    fun start() {
        val track = audioTrack ?: createAudioTrack().also { audioTrack = it }
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
        }
    }

    fun pause() {
        audioTrack?.let { track ->
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
        }
    }

    fun release() {
        audioTrack?.release()
        audioTrack = null
    }

    private fun createAudioTrack(): AudioTrack {
        val format =
            AudioFormat.Builder()
                .setSampleRate(SILENCE_SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        val frameCount = SILENCE_SAMPLE_RATE
        val silence = ShortArray(frameCount)
        return AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(silence.size * Short.SIZE_BYTES)
            .build()
            .apply {
                write(silence, 0, silence.size)
                setLoopPoints(0, frameCount, -1)
                setVolume(0f)
            }
    }

    private companion object {
        const val SILENCE_SAMPLE_RATE = 8_000
    }
}

private fun Drawable.toNotificationLargeIcon(resources: Resources, targetDp: Int = 64): Bitmap {
    val sizePx = (targetDp * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bitmap
}

internal fun TtsQueueState.isPlaying(): Boolean =
    playbackState == TtsQueuePlaybackState.Reading ||
        playbackState == TtsQueuePlaybackState.Preparing

internal fun TtsQueueState.hasActiveMediaSession(): Boolean = currentItem != null

internal fun TtsQueueState.supportedMediaSessionActions(): Long =
    PlaybackStateCompat.ACTION_PLAY or
        PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
        PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_SEEK_TO

internal fun TtsQueueState.toMediaSessionPlaybackState(): Int =
    when {
        currentItem == null -> PlaybackStateCompat.STATE_STOPPED
        playbackState == TtsQueuePlaybackState.Error -> PlaybackStateCompat.STATE_ERROR
        isPlaying() -> PlaybackStateCompat.STATE_PLAYING
        else -> PlaybackStateCompat.STATE_PAUSED
    }
