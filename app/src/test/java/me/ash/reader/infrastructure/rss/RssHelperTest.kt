package me.ash.reader.infrastructure.rss

import android.content.Context
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEntryImpl
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import me.ash.reader.domain.model.feed.Feed
import org.mockito.kotlin.mock

internal const val enclosureUrlString1: String = "https://example.com/enclosure.jpg"
internal const val enclosureUrlString2: String = "https://github.blog/wp-content/uploads/2024/03/github_copilot_header.png"
internal const val imageUrlString: String = "https://example.com/image.jpg"
internal const val enclosureHtmlCase1: String = """
        <enclosure url="$enclosureUrlString1" type="image/jpeg"/>
        <img src="$imageUrlString"/>
    """
internal const val enclosureHtmlCase2: String = """
        <img src="$imageUrlString"/>
        <enclosure url="$enclosureUrlString1" type="image/jpeg"/>
        <img src="$imageUrlString"/> 
    """
internal const val enclosureHtmlCase3: String = """
        <img src="$imageUrlString"/>
        <enclosure url="$enclosureUrlString2" type="image/png"/>
        <img src="$imageUrlString"/> 
    """
internal const val imageHtmlCase1: String = """
        <img src="$enclosureUrlString1"/>
        <img src="$imageUrlString"/> 
    """
internal const val imageHtmlCase2: String = """
        <img src="$imageUrlString"/> 
        <img src="$enclosureUrlString1"/> 
        <img src="$enclosureUrlString1"/> 
    """

@RunWith(MockitoJUnitRunner::class)
class RssHelperTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockIODispatcher: CoroutineDispatcher

    @Mock
    private lateinit var mockOkHttpClient: OkHttpClient

    private lateinit var rssHelper: RssHelper

    @Before
    fun setUp() {
        mockContext = mock<Context> { }
        mockIODispatcher = mock<CoroutineDispatcher> {}
        mockOkHttpClient = mock<OkHttpClient> {}
        rssHelper = RssHelper(mockContext, mockIODispatcher, mockOkHttpClient)
    }

    @Test
    fun testFindThumbnail() {
        Assert.assertNull(rssHelper.findThumbnail(""))
        Assert.assertNull(rssHelper.findThumbnail(" "))
        Assert.assertNull(rssHelper.findThumbnail(null))
        Assert.assertEquals(enclosureUrlString1, rssHelper.findThumbnail(enclosureHtmlCase1))
        Assert.assertEquals(enclosureUrlString1, rssHelper.findThumbnail(enclosureHtmlCase2))
        Assert.assertEquals(enclosureUrlString2, rssHelper.findThumbnail(enclosureHtmlCase3))
        Assert.assertEquals(enclosureUrlString1, rssHelper.findThumbnail(imageHtmlCase1))
        Assert.assertEquals(imageUrlString, rssHelper.findThumbnail(imageHtmlCase2))
    }

    @Test
    fun testEnclosureNoFilenameExtension() {
        val case = """
            <enclosure url="$imageUrlString" type="image/jpeg" length="0"/>
        """
        Assert.assertEquals(imageUrlString, rssHelper.findThumbnail(case))
    }

    @Test
    fun testMediaNamespaceThumbnailInRSS20() {
        val case = """
            <enclosure url="$imageUrlString" type="image/jpeg" length="0"/>
        """
        Assert.assertEquals(imageUrlString, rssHelper.findThumbnail(case))
    }

    @Test
    fun buildArticleFromSyndEntry_strips_wechat_video_control_block_from_raw_and_short_description() {
        val entry =
            SyndEntryImpl().apply {
                link = "https://example.com/post"
                description =
                    SyndContentImpl().apply {
                        type = "text/html"
                        value =
                            """
                            <div>
                              <p><img src="https://example.com/cover.jpg" alt="video cover" /></p>
                              <p>已关注 关注 直播 分享 赞 关闭</p>
                              <p>倍速播放中 0.5倍 1.0倍 超清 流畅</p>
                              <p>视频详情</p>
                              <p>真正的正文从这里开始。</p>
                            </div>
                            """.trimIndent()
                    }
            }

        val article =
            rssHelper.buildArticleFromSyndEntry(
                feed =
                    Feed(
                        id = "feed-1",
                        name = "Feed",
                        url = "https://example.com/feed.xml",
                        groupId = "group-1",
                        accountId = 1,
                    ),
                accountId = 1,
                syndEntry = entry,
            )

        Assert.assertTrue(article.rawDescription.contains("cover.jpg"))
        Assert.assertTrue(article.rawDescription.contains("""class="ry-video-cover""""))
        Assert.assertTrue(article.rawDescription.contains("""data-ry-video-cover="1""""))
        Assert.assertTrue(article.rawDescription.contains("""href="https://example.com/post""""))
        Assert.assertTrue(article.rawDescription.contains("真正的正文从这里开始"))
        Assert.assertFalse(article.rawDescription.contains("倍速播放中"))
        Assert.assertFalse(article.shortDescription.contains("视频详情"))
        Assert.assertTrue(article.shortDescription.contains("真正的正文从这里开始"))
    }
}
