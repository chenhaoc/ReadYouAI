package me.ash.reader.infrastructure.android.ttsqueue

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AbstractRssRepository
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleDaoTtsQueueArticleRepositoryTest {

    @Test
    fun markAsRead_delegates_to_rss_service_instead_of_updating_dao_directly() = runTest {
        val articleDao = mock<ArticleDao>()
        val accountService = mock<AccountService>()
        val rssService = mock<RssService>()
        val rssRepository = mock<AbstractRssRepository>()
        whenever(rssService.get()).thenReturn(rssRepository)

        val repository =
            ArticleDaoTtsQueueArticleRepository(
                articleDao = articleDao,
                accountService = accountService,
                rssService = rssService,
            )

        repository.markAsRead("article-1")

        verify(rssRepository).markAsRead(
            groupId = eq(null),
            feedId = eq(null),
            articleId = eq("article-1"),
            before = eq(null),
            isUnread = eq(false),
        )
        verify(articleDao, never()).markAsReadByArticleId(accountId = any(), articleId = eq("article-1"), isUnread = eq(false))
    }

    @Test
    fun isUnread_returns_false_when_article_state_is_missing() = runTest {
        val articleDao = mock<ArticleDao>()
        val accountService = mock<AccountService>()
        val rssService = mock<RssService>()
        whenever(articleDao.queryIsUnreadByArticleId("article-1")).thenReturn(null)

        val repository =
            ArticleDaoTtsQueueArticleRepository(
                articleDao = articleDao,
                accountService = accountService,
                rssService = rssService,
            )

        val result = repository.isUnread("article-1")

        assertEquals(false, result)
    }
}
