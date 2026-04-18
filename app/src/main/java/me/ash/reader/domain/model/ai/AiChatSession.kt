package me.ash.reader.domain.model.ai

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import me.ash.reader.domain.model.article.Article
import java.util.Date

@Entity(
    tableName = "ai_chat_session",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class AiChatSession(
    @PrimaryKey
    val articleId: String,
    val includeFullContent: Boolean = true,
    val updatedAt: Date = Date(),
)

@Entity(
    tableName = "ai_chat_message",
    foreignKeys = [
        ForeignKey(
            entity = AiChatSession::class,
            parentColumns = ["articleId"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("articleId")],
)
data class AiChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val articleId: String,
    val role: String,
    val content: String,
    val contextType: String,
    val createdAt: Date = Date(),
)

data class AiChatSessionWithMessages(
    @Embedded
    val session: AiChatSession,
    @Relation(parentColumn = "articleId", entityColumn = "articleId")
    val messages: List<AiChatMessage>,
)
