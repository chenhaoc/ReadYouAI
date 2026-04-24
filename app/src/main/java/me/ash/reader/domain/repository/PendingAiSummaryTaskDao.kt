package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.ash.reader.domain.model.ai.PendingAiSummaryTask

@Dao
interface PendingAiSummaryTaskDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tasks: List<PendingAiSummaryTask>)

    @Query(
        """
        SELECT * FROM pending_ai_summary_task
        WHERE accountId = :accountId
        AND nextRunAt <= :now
        ORDER BY nextRunAt ASC, createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun queryRunnableByAccountId(
        accountId: Int,
        now: java.util.Date,
        limit: Int,
    ): List<PendingAiSummaryTask>

    @Query(
        """
        UPDATE pending_ai_summary_task
        SET attemptCount = attemptCount + 1,
            lastAttemptAt = :lastAttemptAt,
            nextRunAt = :nextRunAt
        WHERE articleId = :articleId
        """
    )
    suspend fun scheduleRetry(
        articleId: String,
        lastAttemptAt: java.util.Date,
        nextRunAt: java.util.Date,
    )

    @Query(
        """
        DELETE FROM pending_ai_summary_task
        WHERE articleId IN (:articleIds)
        """
    )
    suspend fun deleteByArticleIds(articleIds: List<String>)

    @Query(
        """
        DELETE FROM pending_ai_summary_task
        WHERE accountId = :accountId
        """
    )
    suspend fun deleteByAccountId(accountId: Int)
}
