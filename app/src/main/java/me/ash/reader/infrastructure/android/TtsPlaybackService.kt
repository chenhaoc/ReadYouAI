package me.ash.reader.infrastructure.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueController
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState
import me.ash.reader.infrastructure.android.ttsqueue.msToSegmentIndex
import me.ash.reader.infrastructure.android.ttsqueue.segmentCharCountsToDurationEstimate
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.ui.page.common.NotificationGroupName

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        }

        startForeground(NOTIFICATION_ID, buildNotification(ttsQueueController.state.value))
        updateWakeLock(ttsQueueController.state.value)
        observeState()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        releaseWakeLock()
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
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    ttsQueueController.resumeCurrent()
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
            })
            isActive = true
        }
    }

    private fun observeState() {
        stateObserverJob?.cancel()
        stateObserverJob = applicationScope.launch {
            ttsQueueController.state.collectLatest { state ->
                updateMediaSession(state)
                updateWakeLock(state)
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
        val isPlaying =
            state.playbackState == TtsQueuePlaybackState.Reading ||
                state.playbackState == TtsQueuePlaybackState.Preparing

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

        val playbackState = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SEEK_TO,
            )
            .setState(
                playbackState,
                durationEstimate?.currentMs ?: 0L,
                if (isPlaying) 1f else 0f,
                SystemClock.elapsedRealtime(),
            )

        session.setPlaybackState(stateBuilder.build())
    }

    private fun buildNotification(state: TtsQueueState): android.app.Notification {
        val currentItem = state.currentItem
        val title = currentItem?.title ?: getString(R.string.tts_playing)
        val subtitle = currentItem?.feedName.orEmpty()
        val isPlaying =
            state.playbackState == TtsQueuePlaybackState.Reading ||
                state.playbackState == TtsQueuePlaybackState.Preparing

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

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

        builder.setStyle(
            MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession?.sessionToken)
        )

        return builder.build()
    }

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
        private const val REQUEST_CODE_PREVIOUS = 1001
        private const val REQUEST_CODE_TOGGLE = 1002
        private const val REQUEST_CODE_NEXT = 1003

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
