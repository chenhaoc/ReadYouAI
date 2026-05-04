package me.ash.reader.ui.page.home.flow

import java.util.Date
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.ui.page.adaptive.dateJumpInitialKey
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowPageIndexMappingTest {

    @Test
    fun lazyListIndexOfDateAccountsForTargetSpacerWhenStickyHeaderEnabled() {
        val items =
            listOf(
                ArticleFlowItem.Date("May 4", showSpacer = false),
                ArticleFlowItem.Article(articleWithFeed("a1")),
                ArticleFlowItem.Date("May 3", showSpacer = true),
                ArticleFlowItem.Article(articleWithFeed("a2")),
            )

        assertEquals(
            3,
            items.lazyListIndexOfDate(
                dateString = "May 3",
                isStickyHeaderEnabled = true,
            ),
        )
    }

    @Test
    fun lazyListIndexOfDateIgnoresSpacerOffsetsWhenStickyHeaderDisabled() {
        val items =
            listOf(
                ArticleFlowItem.Date("May 4", showSpacer = false),
                ArticleFlowItem.Article(articleWithFeed("a1")),
                ArticleFlowItem.Date("May 3", showSpacer = true),
                ArticleFlowItem.Article(articleWithFeed("a2")),
            )

        assertEquals(
            2,
            items.lazyListIndexOfDate(
                dateString = "May 3",
                isStickyHeaderEnabled = false,
            ),
        )
    }

    @Test
    fun lazyListIndexOfArticleAccountsForInsertedSpacerWhenStickyHeaderEnabled() {
        val items =
            listOf(
                ArticleFlowItem.Date("May 4", showSpacer = false),
                ArticleFlowItem.Article(articleWithFeed("a1")),
                ArticleFlowItem.Date("May 3", showSpacer = true),
                ArticleFlowItem.Article(articleWithFeed("a2")),
            )

        assertEquals(4, items.lazyListIndexOfArticle("a2", isStickyHeaderEnabled = true))
        assertEquals(3, items.lazyListIndexOfArticle("a2", isStickyHeaderEnabled = false))
    }

    @Test
    fun dateJumpInitialKeyLoadsOneArticleBeforeTargetDateWhenPossible() {
        assertEquals(0, dateJumpInitialKey(0))
        assertEquals(0, dateJumpInitialKey(1))
        assertEquals(24, dateJumpInitialKey(25))
    }

    private fun articleWithFeed(articleId: String): ArticleWithFeed =
        ArticleWithFeed(
            article =
                Article(
                    id = articleId,
                    date = Date(0L),
                    title = articleId,
                    rawDescription = "",
                    shortDescription = "",
                    link = "https://example.com/$articleId",
                    feedId = "feed-1",
                    accountId = 1,
                ),
            feed =
                Feed(
                    id = "feed-1",
                    name = "Feed",
                    url = "https://example.com/feed.xml",
                    groupId = "group-1",
                    accountId = 1,
                ),
        )
}
