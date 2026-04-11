package me.ash.reader.ui.page.home.reading

import com.google.gson.Gson
import java.security.MessageDigest
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Tag

object ArticleContentBlockParser {

    private val containerTags =
        setOf("body", "article", "section", "main", "div", "span", "figure", "header", "footer")

    fun parse(content: String, baseUrl: String = ""): List<ArticleContentBlock> {
        val document = Jsoup.parseBodyFragment(content, baseUrl)
        val counters = mutableMapOf<ArticleContentBlockType, Int>()
        val blocks = mutableListOf<ArticleContentBlock>()

        document.body().childNodes().forEach { node ->
            collectBlocks(node = node, blocks = blocks, counters = counters)
        }
        return blocks
    }

    fun translationSourcePayload(blocks: List<ArticleContentBlock>): List<TranslationSourceBlock> =
        blocks
            .filter { it.isTranslationEligible }
            .map {
                TranslationSourceBlock(
                    id = it.id,
                    type = it.type.name.lowercase(Locale.US),
                    text = it.translationText.orEmpty(),
                )
            }

    fun translationSourceHash(blocks: List<ArticleContentBlock>): String {
        val payloadJson = Gson().toJson(translationSourcePayload(blocks))
        val digest = MessageDigest.getInstance("SHA-256").digest(payloadJson.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun collectBlocks(
        node: Node,
        blocks: MutableList<ArticleContentBlock>,
        counters: MutableMap<ArticleContentBlockType, Int>,
    ) {
        when (node) {
            is TextNode -> {
                val text = node.text().trim()
                if (text.isNotEmpty()) {
                    blocks +=
                        createBlock(
                            type = ArticleContentBlockType.Paragraph,
                            originalHtml = Element(Tag.valueOf("p"), "").text(text).outerHtml(),
                            translationText = text,
                            counters = counters,
                        )
                }
            }

            is Element -> {
                when (node.tagName()) {
                    "p" -> {
                        if (node.hasClass("readability-styled")) {
                            node.childNodes().forEach { child ->
                                collectBlocks(child, blocks, counters)
                            }
                        } else {
                            val text = node.text().trim()
                            if (text.isNotEmpty()) {
                                blocks +=
                                    createBlock(
                                        type = ArticleContentBlockType.Paragraph,
                                        originalHtml = node.outerHtml(),
                                        translationText = text,
                                        counters = counters,
                                    )
                            }
                        }
                    }

                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        val text = node.text().trim()
                        if (text.isNotEmpty()) {
                            blocks +=
                                createBlock(
                                    type = ArticleContentBlockType.Heading,
                                    originalHtml = node.outerHtml(),
                                    translationText = text,
                                    counters = counters,
                                )
                        }
                    }

                    "blockquote" -> {
                        val text = node.text().trim()
                        if (text.isNotEmpty()) {
                            blocks +=
                                createBlock(
                                    type = ArticleContentBlockType.Quote,
                                    originalHtml = node.outerHtml(),
                                    translationText = text,
                                    counters = counters,
                                )
                        }
                    }

                    "ul" -> {
                        node.children().filter { it.tagName() == "li" }.forEach { listItem ->
                            val text = listItem.text().trim()
                            if (text.isNotEmpty()) {
                                val wrapper = Element(Tag.valueOf("ul"), "")
                                wrapper.appendChild(listItem.clone())
                                blocks +=
                                    createBlock(
                                        type = ArticleContentBlockType.ListItem,
                                        originalHtml = wrapper.outerHtml(),
                                        translationText = text,
                                        counters = counters,
                                    )
                            }
                        }
                    }

                    "ol" -> {
                        node.children().filter { it.tagName() == "li" }.forEachIndexed { index, listItem ->
                            val text = listItem.text().trim()
                            if (text.isNotEmpty()) {
                                val wrapper = Element(Tag.valueOf("ol"), "")
                                wrapper.attr("start", (index + 1).toString())
                                wrapper.appendChild(listItem.clone())
                                blocks +=
                                    createBlock(
                                        type = ArticleContentBlockType.ListItem,
                                        originalHtml = wrapper.outerHtml(),
                                        translationText = text,
                                        counters = counters,
                                    )
                            }
                        }
                    }

                    "pre" -> {
                        blocks +=
                            createBlock(
                                type = ArticleContentBlockType.CodeBlock,
                                originalHtml = node.outerHtml(),
                                counters = counters,
                            )
                    }

                    "code" -> {
                        if (node.parent()?.tagName() != "pre") {
                            val text = node.text().trim()
                            if (text.isNotEmpty()) {
                                blocks +=
                                    createBlock(
                                        type = ArticleContentBlockType.Paragraph,
                                        originalHtml = Element(Tag.valueOf("p"), "").text(text).outerHtml(),
                                        translationText = text,
                                        counters = counters,
                                    )
                            }
                        }
                    }

                    "img" -> {
                        blocks +=
                            createBlock(
                                type = ArticleContentBlockType.Image,
                                originalHtml = node.outerHtml(),
                                counters = counters,
                            )
                    }

                    "iframe", "video" -> {
                        blocks +=
                            createBlock(
                                type = ArticleContentBlockType.Video,
                                originalHtml = node.outerHtml(),
                                counters = counters,
                            )
                    }

                    "hr" -> {
                        blocks +=
                            createBlock(
                                type = ArticleContentBlockType.Divider,
                                originalHtml = node.outerHtml(),
                                counters = counters,
                            )
                    }

                    "table" -> {
                        blocks +=
                            createBlock(
                                type = ArticleContentBlockType.Table,
                                originalHtml = node.outerHtml(),
                                counters = counters,
                            )
                    }

                    else -> {
                        if (node.tagName() in containerTags) {
                            node.childNodes().forEach { child ->
                                collectBlocks(child, blocks, counters)
                            }
                        } else {
                            val text = node.text().trim()
                            if (text.isNotEmpty()) {
                                blocks +=
                                    createBlock(
                                        type = ArticleContentBlockType.Paragraph,
                                        originalHtml = Element(Tag.valueOf("p"), "").text(text).outerHtml(),
                                        translationText = text,
                                        counters = counters,
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createBlock(
        type: ArticleContentBlockType,
        originalHtml: String,
        counters: MutableMap<ArticleContentBlockType, Int>,
        translationText: String? = null,
    ): ArticleContentBlock {
        val count = (counters[type] ?: 0) + 1
        counters[type] = count
        return ArticleContentBlock(
            id = "${type.name.toSnakeCase()}_$count",
            type = type,
            originalHtml = originalHtml,
            translationText = translationText,
        )
    }

    private fun String.toSnakeCase(): String {
        return fold(StringBuilder()) { acc, char ->
            if (char.isUpperCase()) {
                if (acc.isNotEmpty()) acc.append('_')
                acc.append(char.lowercaseChar())
            } else {
                acc.append(char)
            }
            acc
        }.toString()
    }
}
