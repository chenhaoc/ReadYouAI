package me.ash.reader.infrastructure.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import me.ash.reader.infrastructure.android.ttsqueue.ArticleDaoTtsQueueArticleRepository
import me.ash.reader.infrastructure.android.ttsqueue.DataStoreTtsQueueSnapshotStore
import me.ash.reader.infrastructure.android.ttsqueue.TextToSpeechQueuePlaybackClient
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueArticleRepository
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueController
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackClient
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueSnapshotStore
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
    fun provideTtsQueueController(
        snapshotStore: TtsQueueSnapshotStore,
        articleRepository: TtsQueueArticleRepository,
        playbackClient: TtsQueuePlaybackClient,
        @ApplicationScope coroutineScope: CoroutineScope,
    ): TtsQueueController =
        TtsQueueController(
            snapshotStore = snapshotStore,
            articleRepository = articleRepository,
            playbackClient = playbackClient,
            coroutineScope = coroutineScope,
        )
}
