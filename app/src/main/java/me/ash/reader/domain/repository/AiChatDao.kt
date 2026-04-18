package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import me.ash.reader.domain.model.ai.AiChatMessage
import me.ash.reader.domain.model.ai.AiChatSession
import me.ash.reader.domain.model.ai.AiChatSessionWithMessages

@Dao
interface AiChatDao {

    @Transaction
    @Query("SELECT * FROM ai_chat_session WHERE articleId = :articleId")
    suspend fun querySession(articleId: String): AiChatSessionWithMessages?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: AiChatSession): Long

    @Query(
        """
        UPDATE ai_chat_session
        SET includeFullContent = :includeFullContent, updatedAt = :updatedAt
        WHERE articleId = :articleId
        """
    )
    suspend fun updateSession(
        articleId: String,
        includeFullContent: Boolean,
        updatedAt: java.util.Date,
    ): Int

    @Insert
    suspend fun insertMessage(message: AiChatMessage): Long

    @Query("DELETE FROM ai_chat_message WHERE articleId = :articleId")
    suspend fun deleteMessages(articleId: String)
}
