package me.ash.reader.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleReaderRssServiceTest {

    @Test
    fun buildGoogleReaderArticleContent_strips_video_control_noise_from_summary() {
        val summaryHtml =
            """
            <div>
              <p><img src="https://example.com/cover.jpg" alt="video cover" /></p>
              <p>已关注 关注 直播 分享 赞 关闭</p>
              <p>倍速播放中 0.5倍 1.0倍 超清 流畅</p>
              <p>视频详情</p>
              <p>真正的正文从这里开始。</p>
            </div>
            """.trimIndent()

        val content =
            buildGoogleReaderArticleContent(
                summaryContent = summaryHtml,
                articleUrl = "https://example.com/post",
            )

        assertTrue(content.rawDescription.contains("cover.jpg"))
        assertTrue(content.rawDescription.contains("""class="ry-video-cover""""))
        assertTrue(content.rawDescription.contains("""data-ry-video-cover="1""""))
        assertTrue(content.rawDescription.contains("""href="https://example.com/post""""))
        assertTrue(content.rawDescription.contains("真正的正文从这里开始"))
        assertFalse(content.rawDescription.contains("倍速播放中"))
        assertFalse(content.rawDescription.contains("视频详情"))
        assertTrue(content.shortDescription.contains("真正的正文从这里开始"))
        assertFalse(content.shortDescription.contains("倍速播放中"))
        assertFalse(content.shortDescription.contains("视频详情"))
    }

    @Test
    fun buildGoogleReaderArticleContent_keeps_regular_summary_content() {
        val summaryHtml =
            """
            <div>
              <p>这是一段普通摘要。</p>
              <p>里面提到播放、分享和关注，但它不是视频控件。</p>
            </div>
            """.trimIndent()

        val content =
            buildGoogleReaderArticleContent(
                summaryContent = summaryHtml,
                articleUrl = "https://example.com/post",
            )

        assertTrue(content.rawDescription.contains("这是一段普通摘要"))
        assertTrue(content.rawDescription.contains("播放、分享和关注"))
        assertTrue(content.shortDescription.contains("这是一段普通摘要"))
        assertTrue(content.shortDescription.contains("播放、分享和关注"))
    }

    @Test
    fun pendingRemoteIds_strips_account_prefix_from_article_ids() {
        val pendingArticleIds = setOf("1\$remote-a", "2\$remote-b", "remote-c")

        val remoteIds = pendingRemoteIds(pendingArticleIds)

        assertEquals(setOf("remote-a", "remote-b", "remote-c"), remoteIds)
    }

    @Test
    fun remoteIdsWithoutPending_filters_pending_local_article_state() {
        val remoteIds = setOf("remote-a", "remote-b", "remote-c")
        val pendingArticleIds = setOf("1\$remote-b")

        val filtered = remoteIdsWithoutPending(remoteIds, pendingArticleIds)

        assertEquals(setOf("remote-a", "remote-c"), filtered)
    }
}
