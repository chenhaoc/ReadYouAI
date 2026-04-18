package me.ash.reader.infrastructure.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.ash.reader.domain.repository.AiChatRepository
import me.ash.reader.domain.repository.AiSummaryRepository
import me.ash.reader.domain.repository.AiTranslationRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OpenAiModule {
    @Provides
    @Singleton
    fun provideAiSummaryRepository(): AiSummaryRepository = AiSummaryRepository()

    @Provides
    @Singleton
    fun provideAiTranslationRepository(): AiTranslationRepository = AiTranslationRepository()

    @Provides
    @Singleton
    fun provideAiChatRepository(): AiChatRepository = AiChatRepository()
}
