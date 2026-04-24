package me.ash.reader.infrastructure.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import me.ash.reader.infrastructure.android.TtsPlaybackService
import me.ash.reader.infrastructure.android.ttsqueue.ArticleDaoTtsQueueArticleRepository
import me.ash.reader.infrastructure.android.ttsqueue.DataStoreTtsQueueSnapshotStore
import me.ash.reader.infrastructure.android.ttsqueue.TextToSpeechQueuePlaybackClient
import me.ash.reader.infrastructure.android.ttsqueue.TtsPlaybackServiceLauncher
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueArticleRepository
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueController
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackClient
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueSnapshotStore
import me.ash.reader.infrastructure.preference.SettingsProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TtsQueueModule {

    @Provides
    @Singleton
    fun provideTtsQueueSnapshotStore(
        impl: DataStoreTtsQueueSnapshotStore,
    ): TtsQueueSnapshotStore = impl

    @Provides
    @Singleton
    fun provideTtsQueueArticleRepository(
        impl: ArticleDaoTtsQueueArticleRepository,
    ): TtsQueueArticleRepository = impl

    @Provides
    @Singleton
    fun provideTtsQueuePlaybackClient(
        impl: TextToSpeechQueuePlaybackClient,
    ): TtsQueuePlaybackClient = impl

    @Provides
    @Singleton
    fun provideTtsPlaybackServiceLauncher(
        @ApplicationContext context: Context,
    ): TtsPlaybackServiceLauncher = object : TtsPlaybackServiceLauncher {
        override fun startService() {
            TtsPlaybackService.startService(context)
        }

        override fun stopService() {
            TtsPlaybackService.stopService(context)
        }
    }

    @Provides
    @Singleton
    fun provideTtsQueueController(
        snapshotStore: TtsQueueSnapshotStore,
        articleRepository: TtsQueueArticleRepository,
        playbackClient: TtsQueuePlaybackClient,
        serviceLauncher: TtsPlaybackServiceLauncher,
        settingsProvider: SettingsProvider,
        @ApplicationScope coroutineScope: CoroutineScope,
    ): TtsQueueController =
        TtsQueueController(
            snapshotStore = snapshotStore,
            articleRepository = articleRepository,
            playbackClient = playbackClient,
            serviceLauncher = serviceLauncher,
            coroutineScope = coroutineScope,
            markReadOnCommuteComplete = { settingsProvider.settings.commuteBriefMarkReadOnComplete.value },
        )
}
