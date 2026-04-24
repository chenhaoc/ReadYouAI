package me.ash.reader.domain.model.ai

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import me.ash.reader.domain.model.article.Article

@Entity(
    tableName = "pending_ai_summary_task",
    foreignKeys = [
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["accountId"]), Index(value = ["createdAt"]), Index(value = ["nextRunAt"])],
)
data class PendingAiSummaryTask(
    @PrimaryKey
    val articleId: String,
    @ColumnInfo
    val accountId: Int,
    val createdAt: Date,
    @ColumnInfo(defaultValue = "0")
    val attemptCount: Int = 0,
    val lastAttemptAt: Date? = null,
    @ColumnInfo(defaultValue = "0")
    val nextRunAt: Date = Date(0L),
)
