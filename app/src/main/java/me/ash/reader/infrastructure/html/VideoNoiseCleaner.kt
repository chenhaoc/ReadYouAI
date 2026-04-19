package me.ash.reader.infrastructure.html

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Tag

object VideoNoiseCleaner {

    const val VIDEO_COVER_ATTR = "data-ry-video-cover"
    const val VIDEO_COVER_CLASS = "ry-video-cover"
    private const val SANITIZED_CLASS_ATTR = "data-sanitized-class"
    private const val SANITIZED_ID_ATTR = "data-sanitized-id"
    private const val VIDEO_COVER_VALUE = "1"
    private const val MAX_DIRECTIONAL_SCAN = 14
    private const val MAX_NEUTRAL_GAP_AFTER_SIGNAL = 2
    private val mediaTags = setOf("img", "video", "iframe")
    private val mediaSelector = mediaTags.joinToString(",")
    private val controlKeywords =
        listOf(
            "已关注",
            "关注",
            "直播",
            "分享",
            "分享视频",
            "赞",
            "点赞",
            "在看",
            "转载",
            "关闭",
            "观看更多",
            "退出全屏",
            "全屏",
            "切换到竖屏",
            "切换横屏模式",
            "切换到横屏模式",
            "继续播放",
            "进度条",
            "播放",
            "倍速",
            "倍速播放中",
            "0.5倍",
            "0.75倍",
            "1.0倍",
            "1.5倍",
            "2.0倍",
            "超清",
            "流畅",
            "继续观看",
            "已同步到看一看",
            "看一看",
            "写下你的评论",
            "视频详情",
        )
    private val strongKeywords =
        setOf("已关注", "倍速播放中", "继续观看", "写下你的评论", "视频详情")
    private val playbackPattern =
        Regex("""(\d{1,2}:\d{2})|(\d+(?:\.\d+)?倍)|(\d+/\d+)|([0-9]{1,3}%)""")

    fun cleanHtml(htmlContent: String?, baseUrl: String? = null): String {
        if (htmlContent.isNullOrBlank()) return htmlContent.orEmpty()
        val document = Jsoup.parseBodyFragment(htmlContent, baseUrl.orEmpty())
        cleanElement(document.body())
        return document.body().html()
    }

    fun isVideoCoverLink(element: Element): Boolean {
        return element.tagName() == "a" && element.attr(VIDEO_COVER_ATTR) == VIDEO_COVER_VALUE
    }

    fun cleanElement(root: Element) {
        normalizeWechatVideoEmbeds(root)
        root.children().toList().forEach(::cleanElement)

        val siblings = root.childNodes().toList()
        siblings.forEachIndexed { index, node ->
            val element = node as? Element ?: return@forEachIndexed
            if (!isMediaAnchorElement(element)) return@forEachIndexed

            val noiseNodes = collectNoiseRun(anchorIndex = index, siblings = siblings).distinct()
            if (noiseNodes.isEmpty()) return@forEachIndexed

            noiseNodes.forEach(Node::remove)
            promoteMediaAnchorToVideoCover(element, articleLink = root.baseUri())
        }
    }

    private fun normalizeWechatVideoEmbeds(root: Element) {
        root.getAllElements().toList().forEach { element ->
            if (!isWechatVideoEmbed(element)) return@forEach
            val posterUrl = extractWechatVideoPoster(element) ?: return@forEach
            val replacement = createLinkedCoverImage(posterUrl = posterUrl, articleLink = root.baseUri())

            element.nextElementSibling()
                ?.takeIf(::isWechatVideoPlaceholder)
                ?.remove()

            element.replaceWith(replacement)
        }
    }

    private fun promoteMediaAnchorToVideoCover(
        element: Element,
        articleLink: String?,
    ) {
        when {
            isVideoCoverLink(element) -> ensureVideoCoverLink(element)

            element.tagName() == "a" &&
                element.selectFirst("img") != null &&
                element.text().isBlank() -> {
                ensureVideoCoverLink(element)
            }

            else -> {
                val nestedLink =
                    element.selectFirst("a")
                        ?.takeIf { it.selectFirst("img") != null && it.text().isBlank() }

                if (nestedLink != null) {
                    ensureVideoCoverLink(nestedLink)
                    return
                }

                val image = element.selectFirst("img") ?: return
                val replacement =
                    createLinkedCoverImage(image = image.clone(), articleLink = articleLink)
                image.replaceWith(replacement)
            }
        }
    }

    private fun collectNoiseRun(
        anchorIndex: Int,
        siblings: List<Node>,
    ): List<Node> =
        collectDirectionalRun(anchorIndex = anchorIndex, siblings = siblings, step = -1) +
            collectDirectionalRun(anchorIndex = anchorIndex, siblings = siblings, step = 1)

    private fun collectDirectionalRun(
        anchorIndex: Int,
        siblings: List<Node>,
        step: Int,
    ): List<Node> {
        val visitedIndices = mutableListOf<Int>()
        val signalIndices = mutableListOf<Int>()
        var nodesSinceLastSignal = 0
        var strongSignalCount = 0
        var started = false

        var index = anchorIndex + step
        while (index in siblings.indices && visitedIndices.size < MAX_DIRECTIONAL_SCAN) {
            val node = siblings[index]
            if (node.parent() == null) {
                index += step
                continue
            }

            if (node.isBlankTextNode()) {
                visitedIndices += index
                index += step
                continue
            }

            val signal = analyzeNode(node)
            if (!started) {
                if (!signal.isLikelyControlNode) {
                    break
                }
                started = true
            }

            visitedIndices += index
            if (signal.isLikelyControlNode) {
                signalIndices += index
                nodesSinceLastSignal = 0
                if (signal.hasStrongKeyword) {
                    strongSignalCount += 1
                }
            } else {
                nodesSinceLastSignal += 1
                if (nodesSinceLastSignal > MAX_NEUTRAL_GAP_AFTER_SIGNAL) {
                    visitedIndices.removeLast()
                    break
                }
            }

            index += step
        }

        if (signalIndices.size < 2 && strongSignalCount == 0) {
            return emptyList()
        }

        val start = signalIndices.minOrNull() ?: return emptyList()
        val end = signalIndices.maxOrNull() ?: return emptyList()
        return visitedIndices
            .filter { it in start..end }
            .map(siblings::get)
    }

    private fun extractWechatVideoPoster(element: Element): String? {
        element.selectFirst("video[poster]")
            ?.attr("poster")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        listOf("data-cover", "data-feedcoverurl", "data-feedfullcoverurl", "data-feedthumburl")
            .firstNotNullOfOrNull { attribute ->
                element.attr(attribute)
                    .takeIf(String::isNotBlank)
                    ?.let(::decodePossiblyEncodedUrl)
                    ?.takeIf(String::isNotBlank)
            }
            ?.let { return it }

        return null
    }

    private fun createLinkedCoverImage(
        posterUrl: String,
        articleLink: String?,
    ): Element {
        val image =
            Element(Tag.valueOf("img"), "").apply {
                attr("src", posterUrl)
                attr("alt", "video cover")
            }
        return createLinkedCoverImage(image = image, articleLink = articleLink)
    }

    private fun createLinkedCoverImage(
        image: Element,
        articleLink: String?,
    ): Element {
        val link = articleLink?.takeIf(String::isNotBlank) ?: return image
        return Element(Tag.valueOf("a"), "").apply {
            attr("href", link)
            ensureVideoCoverLink(this)
            appendChild(image)
        }
    }

    private fun ensureVideoCoverLink(link: Element) {
        link.addClass(VIDEO_COVER_CLASS)
        link.attr(VIDEO_COVER_ATTR, VIDEO_COVER_VALUE)
    }

    private fun decodePossiblyEncodedUrl(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8) }
            .getOrDefault(value)

    private fun isWechatVideoPlaceholder(element: Element): Boolean {
        return element.hasEffectiveClassName("wx_widget_placeholder") ||
            (element.tagName() == "span" &&
                element.hasEffectiveClassName("js_img_placeholder") &&
                (element.hasAttr("data-vid") || element.hasEffectiveClassName("wx_widget_placeholder")))
    }

    private fun isMediaAnchorElement(element: Element): Boolean {
        val tagName = element.tagName()
        if (tagName in mediaTags || tagName == "figure") return true

        if (element.ownText().isNotBlank()) return false
        if (element.text().isNotBlank()) return false

        return element.select(mediaSelector).isNotEmpty()
    }

    private fun analyzeNode(node: Node): NodeSignal {
        val element = node as? Element
        val normalizedText = node.textContentNormalized()
        val keywordHits = controlKeywords.count(normalizedText::contains)
        val hasStrongKeyword = strongKeywords.any(normalizedText::contains)
        val hasPlaybackSignal = playbackPattern.containsMatchIn(normalizedText)
        val hasControlClassHint =
            element?.effectiveClassNames()?.any(::isControlClassName) == true ||
                element?.effectiveIdNames()?.any(::isControlClassName) == true
        val hasControlDescendants =
            element?.select("button,input,select,textarea,[role=button]")?.isNotEmpty() == true
        val isLikelyControlNode =
            hasControlClassHint ||
                hasControlDescendants ||
                keywordHits >= 2 ||
                (keywordHits >= 1 && (hasPlaybackSignal || normalizedText.length <= 80)) ||
                (hasPlaybackSignal && normalizedText.length <= 32)

        return NodeSignal(
            hasStrongKeyword = hasStrongKeyword,
            isLikelyControlNode = isLikelyControlNode,
        )
    }

    private fun Node.isBlankTextNode(): Boolean = this is TextNode && text().isBlank()

    private fun Node.textContentNormalized(): String =
        when (this) {
            is TextNode -> text()
            is Element -> text()
            else -> outerHtml()
        }.replace("\\s+".toRegex(), "")

    private fun isControlClassName(value: String): Boolean {
        val normalized = value.lowercase()
        return normalized.contains("control") ||
            normalized.contains("player") ||
            normalized.contains("video-info") ||
            normalized.contains("screen-reader") ||
            normalized.contains("sr-only") ||
            normalized.contains("visually-hidden")
    }

    private fun isWechatVideoEmbed(element: Element): Boolean {
        if (element.tagName() == "mp-common-videosnap") return true
        return element.tagName() == "span" &&
            element.hasEffectiveClassName("video_iframe") &&
            element.hasEffectiveClassName("rich_pages")
    }

    private fun Element.hasEffectiveClassName(name: String): Boolean = effectiveClassNames().contains(name)

    private fun Element.effectiveClassNames(): Set<String> =
        buildSet {
            addAll(classNames())
            attr(SANITIZED_CLASS_ATTR)
                .split("\\s+".toRegex())
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach(::add)
        }

    private fun Element.effectiveIdNames(): Set<String> =
        buildSet {
            id().trim().takeIf(String::isNotBlank)?.let(::add)
            attr(SANITIZED_ID_ATTR).trim().takeIf(String::isNotBlank)?.let(::add)
        }

    private data class NodeSignal(
        val hasStrongKeyword: Boolean,
        val isLikelyControlNode: Boolean,
    )
}
