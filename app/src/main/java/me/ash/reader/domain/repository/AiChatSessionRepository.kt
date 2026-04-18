package me.ash.reader.domain.repository

import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.domain.model.ai.AiChatMessage
import me.ash.reader.domain.model.ai.AiChatSession
import me.ash.reader.domain.model.ai.AiChatSessionWithMessages

@Singleton
class AiChatSessionRepository @Inject constructor(
    private val aiChatDao: AiChatDao,
) {

    suspend fun querySession(articleId: String): AiChatSessionWithMessages? =
        aiChatDao.querySession(articleId)?.let { session ->
            session.copy(
                messages = session.messages.sortedWith(compareBy({ it.createdAt.time }, { it.id })),
            )
        }

    suspend fun upsertSession(
        articleId: String,
        includeFullContent: Boolean,
        updatedAt: Date = Date(),
    ) {
        val session =
            AiChatSession(
                articleId = articleId,
                includeFullContent = includeFullContent,
                updatedAt = updatedAt,
            )
        val insertedId = aiChatDao.insertSession(session)
        if (insertedId == -1L) {
            aiChatDao.updateSession(
                articleId = articleId,
                includeFullContent = includeFullContent,
                updatedAt = updatedAt,
            )
        }
    }

    suspend fun appendMessage(
        articleId: String,
        role: String,
        content: String,
        contextType: String,
        createdAt: Date = Date(),
    ): AiChatMessage {
        val message = AiChatMessage(
            articleId = articleId,
            role = role,
            content = content,
            contextType = contextType,
            createdAt = createdAt,
        )
        val id = aiChatDao.insertMessage(message)
        return message.copy(id = id)
    }

    suspend fun clearMessages(articleId: String) {
        aiChatDao.deleteMessages(articleId)
    }
}
