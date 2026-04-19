package me.ash.reader.infrastructure.html

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNoiseCleanerTest {

    @Test
    fun cleanHtml_removes_wechat_video_control_block_but_keeps_media_and_body() {
        val html =
            """
            <div>
              <p><img src="https://example.com/cover.jpg" alt="video cover" /></p>
              <p>已关注 关注 直播 分享 赞 关闭</p>
              <p>0/0 00:00 / 00:22 倍速播放中 0.5倍 1.0倍 1.5倍 2.0倍</p>
              <p>超清 流畅 继续观看 视频详情</p>
              <p>真正的正文从这里开始。</p>
            </div>
            """.trimIndent()

        val cleaned = VideoNoiseCleaner.cleanHtml(html, "https://example.com/post")

        assertTrue(cleaned.contains("cover.jpg"))
        assertTrue(cleaned.contains("""class="ry-video-cover""""))
        assertTrue(cleaned.contains("""data-ry-video-cover="1""""))
        assertTrue(cleaned.contains("""href="https://example.com/post""""))
        assertTrue(cleaned.contains("真正的正文从这里开始"))
        assertFalse(cleaned.contains("倍速播放中"))
        assertFalse(cleaned.contains("视频详情"))
    }

    @Test
    fun cleanHtml_keeps_regular_caption_after_image() {
        val html =
            """
            <div>
              <p><img src="https://example.com/image.jpg" alt="cover" /></p>
              <p>图注：这是普通图片说明。</p>
              <p>后面的正文依然保留。</p>
            </div>
            """.trimIndent()

        val cleaned = VideoNoiseCleaner.cleanHtml(html, "https://example.com")

        assertTrue(cleaned.contains("图注：这是普通图片说明"))
        assertTrue(cleaned.contains("后面的正文依然保留"))
    }

    @Test
    fun cleanHtml_does_not_strip_control_words_outside_media_neighborhood() {
        val html = "<p>这段正文提到播放、分享和关注，但不是视频控件。</p>"

        val cleaned = VideoNoiseCleaner.cleanHtml(html, "https://example.com")

        assertTrue(cleaned.contains("播放、分享和关注"))
    }

    @Test
    fun cleanHtml_removes_control_blocks_before_and_after_media_anchor() {
        val html =
            """
            <div>
              <p>在拿着Vidu Q3也是手拿把掐：</p>
              <ul><li>锁定参考图。</li></ul>
              <p>已关注</p>
              <p>0:00 / 00:06 切换横屏模式 继续播放</p>
              <p>倍速播放中 0.5倍 1.0倍 超清 流畅</p>
              <p><img src="https://example.com/video.jpg" alt="video" /></p>
              <p>继续观看</p>
              <p>新Vidu Q3参考生，这是冲着「剧」来的！万物皆可参考：特效音效场景都备好了</p>
              <p>观看更多</p>
              <p>转载</p>
              <p>量子位 已关注 分享 点赞 在看 已同步到看一看 写下你的评论 视频详情</p>
              <p>像下面这个火焰特效，是直接可以在中文里使用的程度：</p>
            </div>
            """.trimIndent()

        val cleaned = VideoNoiseCleaner.cleanHtml(html, "https://example.com/article")

        assertTrue(cleaned.contains("在拿着Vidu Q3也是手拿把掐"))
        assertTrue(cleaned.contains("video.jpg"))
        assertTrue(cleaned.contains("""class="ry-video-cover""""))
        assertTrue(cleaned.contains("""data-ry-video-cover="1""""))
        assertTrue(cleaned.contains("""href="https://example.com/article""""))
        assertTrue(cleaned.contains("像下面这个火焰特效"))
        assertFalse(cleaned.contains("倍速播放中"))
        assertFalse(cleaned.contains("继续观看"))
        assertFalse(cleaned.contains("观看更多"))
        assertFalse(cleaned.contains("视频详情"))
        assertFalse(cleaned.contains("新Vidu Q3参考生"))
    }

    @Test
    fun cleanHtml_handles_media_wrapped_by_link_container() {
        val html =
            """
            <div>
              <p>00:00 / 00:06 切换横屏模式 继续播放</p>
              <p>倍速播放中 0.5倍 1.0倍 超清 流畅</p>
              <p><a href="https://example.com/video"><img src="https://example.com/video.jpg" alt="video" /></a></p>
              <p>继续观看</p>
              <p>观看更多</p>
              <p>量子位 已关注 分享 点赞 在看 写下你的评论 视频详情</p>
              <p>真正的正文保留。</p>
            </div>
            """.trimIndent()

        val cleaned = VideoNoiseCleaner.cleanHtml(html, "https://example.com/post")

        assertTrue(cleaned.contains("video.jpg"))
        assertTrue(cleaned.contains("""class="ry-video-cover""""))
        assertTrue(cleaned.contains("""data-ry-video-cover="1""""))
        assertTrue(cleaned.contains("""href="https://example.com/video""""))
        assertTrue(cleaned.contains("真正的正文保留"))
        assertFalse(cleaned.contains("倍速播放中"))
        assertFalse(cleaned.contains("继续观看"))
        assertFalse(cleaned.contains("视频详情"))
    }

    @Test
    fun cleanHtml_replaces_wechat_video_widget_with_cover_image() {
        val html =
            """
            <div>
              <span class="video_iframe rich_pages" data-cover="https%3A%2F%2Fexample.com%2Fcover.jpg">
                <div class="video_tail_module">已关注 关注 分享 赞</div>
                <div class="video_quick_play_context">倍速播放中</div>
                <div class="video_full-screen__sub-setting__speed">0.5倍 1.0倍 1.5倍</div>
                <div class="video_full-screen__sub-setting__ratio">超清 流畅</div>
                <div class="video_poster__info">
                  <p class="video_poster__info__title">继续观看</p>
                  <p class="video_poster__info__desc">新Vidu Q3参考生，这是冲着「剧」来的！</p>
                </div>
                <div class="interact_video"><a id="video_detail_btn">视频详情</a></div>
              </span>
              <span class="js_img_placeholder wx_widget_placeholder" data-vid="wxv_1"></span>
              <p>真正的正文保留。</p>
            </div>
            """.trimIndent()

        val cleaned = VideoNoiseCleaner.cleanHtml(html, "https://example.com/article")

        assertTrue(cleaned.contains("""href="https://example.com/article""""))
        assertTrue(cleaned.contains("""class="ry-video-cover""""))
        assertTrue(cleaned.contains("""data-ry-video-cover="1""""))
        assertTrue(cleaned.contains("""<img src="https://example.com/cover.jpg""""))
        assertTrue(cleaned.contains("真正的正文保留"))
        assertFalse(cleaned.contains("video_iframe"))
        assertFalse(cleaned.contains("倍速播放中"))
        assertFalse(cleaned.contains("继续观看"))
        assertFalse(cleaned.contains("视频详情"))
    }
}
