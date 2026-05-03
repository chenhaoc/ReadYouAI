package me.ash.reader.ui.page.home.reading

import me.ash.reader.domain.model.ai.AiChatMessage
import org.jsoup.nodes.Entities

private val codeFenceRegex = Regex("""^```([a-zA-Z0-9_+-]+)?\s*$""")
private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
private val unorderedListRegex = Regex("""^(\s*)([-*+])\s+(.*)$""")
private val orderedListRegex = Regex("""^(\s*)(\d+)\.\s+(.*)$""")
private val taskListRegex = Regex("""^\[( |x|X)]\s+(.*)$""")
private val blockQuoteRegex = Regex("""^\s*>\s?(.*)$""")
private val dividerRegex = Regex("""^\s{0,3}([-*_])(?:\s*\1){2,}\s*$""")
private val tableSeparatorRegex = Regex("""^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*:?-{3,}:?\s*\|?\s*$""")

sealed interface AiChatMarkdownBlock {
    data class Heading(
        val level: Int,
        val content: String,
    ) : AiChatMarkdownBlock

    data class Paragraph(
        val content: String,
    ) : AiChatMarkdownBlock

    data class ListBlock(
        val items: List<ListItem>,
    ) : AiChatMarkdownBlock

    data class Quote(
        val lines: List<String>,
    ) : AiChatMarkdownBlock

    data class CodeBlock(
        val language: String?,
        val code: String,
    ) : AiChatMarkdownBlock

    data object Divider : AiChatMarkdownBlock

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : AiChatMarkdownBlock
}

data class ListItem(
    val marker: String,
    val content: String,
    val depth: Int = 0,
    val isTask: Boolean = false,
)

private data class ListNode(
    val item: ListItem,
    val children: MutableList<ListNode> = mutableListOf(),
)

private enum class HtmlListType {
    Ordered,
    Unordered,
}

fun parseAiChatMarkdownBlocks(markdown: String): List<AiChatMarkdownBlock> {
    val normalized = markdown.replace("\r\n", "\n").trim()
    if (normalized.isBlank()) return emptyList()

    val lines = normalized.lines()
    val blocks = mutableListOf<AiChatMarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index += 1
            continue
        }

        codeFenceRegex.matchEntire(line)?.let { match ->
            val language = match.groupValues[1].ifBlank { null }
            index += 1
            val codeLines = mutableListOf<String>()
            while (index < lines.size && !codeFenceRegex.matches(lines[index])) {
                codeLines += lines[index]
                index += 1
            }
            if (index < lines.size && codeFenceRegex.matches(lines[index])) {
                index += 1
            }
            blocks += AiChatMarkdownBlock.CodeBlock(language = language, code = codeLines.joinToString("\n"))
            continue
        }

        if (dividerRegex.matches(line)) {
            blocks += AiChatMarkdownBlock.Divider
            index += 1
            continue
        }

        headingRegex.matchEntire(line)?.let { match ->
            blocks +=
                AiChatMarkdownBlock.Heading(
                    level = match.groupValues[1].length.coerceIn(1, 6),
                    content = match.groupValues[2].trim(),
                )
            index += 1
            continue
        }

        if (
            isTableHeaderLine(line) &&
            index + 1 < lines.size &&
            tableSeparatorRegex.matches(lines[index + 1])
        ) {
            val headers = splitTableRow(line)
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && isTableBodyLine(lines[index])) {
                rows += splitTableRow(lines[index])
                index += 1
            }
            blocks += AiChatMarkdownBlock.Table(headers = headers, rows = rows)
            continue
        }

        if (unorderedListRegex.matches(line) || orderedListRegex.matches(line)) {
            val items = mutableListOf<ListItem>()
            while (index < lines.size) {
                val current = lines[index]
                val unordered = unorderedListRegex.matchEntire(current)
                val ordered = orderedListRegex.matchEntire(current)
                val match = unordered ?: ordered ?: break
                val indent = match.groupValues[1].replace("\t", "    ").length
                val depth = (indent / 2).coerceAtLeast(0)
                val marker = if (unordered != null) "\u2022" else "${match.groupValues[2]}."
                val rawContent = match.groupValues[3]
                val taskMatch = taskListRegex.matchEntire(rawContent.trim())
                items +=
                    if (taskMatch != null) {
                        ListItem(
                            marker = if (taskMatch.groupValues[1].equals("x", ignoreCase = true)) "☑" else "☐",
                            content = taskMatch.groupValues[2].trim(),
                            depth = depth,
                            isTask = true,
                        )
                    } else {
                        ListItem(
                            marker = marker,
                            content = rawContent.trim(),
                            depth = depth,
                        )
                    }
                index += 1
            }
            blocks += AiChatMarkdownBlock.ListBlock(items = items)
            continue
        }

        if (blockQuoteRegex.matches(line)) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size) {
                val match = blockQuoteRegex.matchEntire(lines[index]) ?: break
                quoteLines += match.groupValues[1].trim()
                index += 1
            }
            blocks += AiChatMarkdownBlock.Quote(lines = quoteLines)
            continue
        }

        val paragraphLines = mutableListOf<String>()
        while (index < lines.size) {
            val current = lines[index]
            if (current.isBlank()) break
            if (
                codeFenceRegex.matches(current) ||
                dividerRegex.matches(current) ||
                headingRegex.matches(current) ||
                (isTableHeaderLine(current) &&
                    index + 1 < lines.size &&
                    tableSeparatorRegex.matches(lines[index + 1])) ||
                unorderedListRegex.matches(current) ||
                orderedListRegex.matches(current) ||
                blockQuoteRegex.matches(current)
            ) {
                break
            }
            paragraphLines += current.trim()
            index += 1
        }
        blocks += AiChatMarkdownBlock.Paragraph(content = paragraphLines.joinToString(separator = "\n"))
    }

    return blocks
}

fun buildAiChatMarkdownHtml(markdown: String): String {
    val blocks = parseAiChatMarkdownBlocks(markdown)
    if (blocks.isEmpty()) return ""

    return buildString {
        append("""<section class="ry-ai-chat-markdown">""")
        blocks.forEach { block ->
            append(block.toHtml())
        }
        append("</section>")
    }
}

fun buildAiChatConversationHtml(
    messages: List<AiChatMessage>,
    isSending: Boolean,
): String {
    if (messages.isEmpty() && !isSending) return ""

    return buildString {
        append("""<section class="ry-ai-chat-conversation">""")
        messages.forEach { message ->
            append(message.toConversationMessageHtml())
        }
        if (isSending) {
            append(
                """
                <div class="ry-ai-chat-message assistant pending">
                    <div class="ry-ai-chat-bubble">
                        <div class="ry-ai-chat-typing" aria-label="loading">
                            <span></span><span></span><span></span>
                        </div>
                    </div>
                </div>
                """.trimIndent()
            )
        }
        append("</section>")
    }
}

private fun AiChatMessage.toConversationMessageHtml(): String {
    val roleClass =
        if (role == AI_CHAT_ROLE_USER) {
            "user"
        } else {
            "assistant"
        }
    val contentHtml =
        if (role == AI_CHAT_ROLE_USER) {
            buildAiChatPlainTextHtml(content)
        } else {
            buildAiChatMarkdownHtml(content)
        }
    return """
        <div class="ry-ai-chat-message $roleClass">
            <div class="ry-ai-chat-bubble">$contentHtml</div>
        </div>
    """.trimIndent()
}

private fun AiChatMarkdownBlock.toHtml(): String =
    when (this) {
        is AiChatMarkdownBlock.Heading ->
            buildHtmlTag(
                tag = "h$level",
                content = buildAiChatInlineHtml(content),
            )

        is AiChatMarkdownBlock.Paragraph ->
            buildHtmlTag(
                tag = "p",
                content = buildAiChatInlineHtml(content),
            )

        is AiChatMarkdownBlock.ListBlock -> buildListHtml(items)

        is AiChatMarkdownBlock.Quote ->
            buildHtmlTag(
                tag = "blockquote",
                content = buildHtmlTag("p", buildAiChatInlineHtml(lines.joinToString("\n"))),
            )

        is AiChatMarkdownBlock.CodeBlock ->
            buildString {
                language?.takeIf { it.isNotBlank() }?.let {
                    append(
                        buildHtmlTag(
                            tag = "div class=\"ry-ai-chat-code-language\"",
                            content = escapeHtml(it),
                        )
                    )
                }
                append(
                    "<pre><code" +
                        language?.takeIf { it.isNotBlank() }?.let {
                            " class=\"language-${escapeHtmlAttribute(it)}\""
                        }.orEmpty() +
                        ">${escapeHtml(code)}</code></pre>"
                )
            }

        is AiChatMarkdownBlock.Divider -> "<hr />"

        is AiChatMarkdownBlock.Table ->
            buildString {
                append("""<div class="ry-ai-chat-table-wrap"><table><thead><tr>""")
                headers.forEach { header ->
                    append(buildHtmlTag("th", buildAiChatInlineHtml(header)))
                }
                append("</tr></thead><tbody>")
                rows.forEach { row ->
                    append("<tr>")
                    row.forEach { cell ->
                        append(buildHtmlTag("td", buildAiChatInlineHtml(cell)))
                    }
                    append("</tr>")
                }
                append("</tbody></table></div>")
            }
    }

private fun buildListHtml(items: List<ListItem>): String {
    if (items.isEmpty()) return ""

    val roots = mutableListOf<ListNode>()
    val stack = mutableListOf<ListNode>()

    items.forEach { item ->
        val normalizedDepth = item.depth.coerceAtLeast(0).coerceAtMost(stack.size)
        while (stack.size > normalizedDepth) {
            stack.removeLast()
        }

        val node = ListNode(item = item)
        if (stack.isEmpty()) {
            roots += node
        } else {
            stack.last().children += node
        }
        stack += node
    }

    return renderListNodes(roots)
}

private fun renderListNodes(nodes: List<ListNode>): String =
    buildString {
        var index = 0
        while (index < nodes.size) {
            val listType = nodes[index].item.toHtmlListType()
            append(openListTag(listType, nodes[index].item))

            while (index < nodes.size && nodes[index].item.toHtmlListType() == listType) {
                val node = nodes[index]
                append("<li>")
                if (node.item.isTask) {
                    append(
                        buildHtmlTag(
                            tag = "span class=\"ry-ai-chat-task-marker\"",
                            content = escapeHtml(node.item.marker),
                        )
                    )
                    append(' ')
                }
                append(buildAiChatInlineHtml(node.item.content))
                if (node.children.isNotEmpty()) {
                    append(renderListNodes(node.children))
                }
                append("</li>")
                index += 1
            }

            append(closeListTag(listType))
        }
    }

private fun ListItem.toHtmlListType(): HtmlListType =
    if (marker.endsWith(".") && marker.dropLast(1).all(Char::isDigit)) {
        HtmlListType.Ordered
    } else {
        HtmlListType.Unordered
    }

private fun openListTag(
    type: HtmlListType,
    item: ListItem,
): String =
    when (type) {
        HtmlListType.Ordered -> {
            val start = item.marker.removeSuffix(".").toIntOrNull()
            if (start != null && start > 1) {
                """<ol start="$start">"""
            } else {
                "<ol>"
            }
        }

        HtmlListType.Unordered -> "<ul>"
    }

private fun closeListTag(type: HtmlListType): String =
    when (type) {
        HtmlListType.Ordered -> "</ol>"
        HtmlListType.Unordered -> "</ul>"
    }

private fun buildAiChatInlineHtml(text: String): String {
    fun parseSegment(
        source: String,
        allowAutolinks: Boolean = true,
    ): String =
        buildString {
            var index = 0
            while (index < source.length) {
                when {
                    allowAutolinks &&
                        (source.startsWith("<http://", index) || source.startsWith("<https://", index)) -> {
                        val urlEnd = source.indexOf('>', startIndex = index + 1)
                        if (urlEnd != -1) {
                            val url = source.substring(index + 1, urlEnd)
                            append("""<a href="${escapeHtmlAttribute(url)}">${escapeHtml(url)}</a>""")
                            index = urlEnd + 1
                            continue
                        }
                    }

                    allowAutolinks &&
                        (source.startsWith("http://", index) || source.startsWith("https://", index)) -> {
                        val end = findInlineUrlEnd(source, index)
                        val candidate = source.substring(index, end)
                        val url = trimTrailingUrlPunctuation(candidate)
                        if (url.isNotBlank()) {
                            val suffix = candidate.substring(url.length)
                            append("""<a href="${escapeHtmlAttribute(url)}">${escapeHtml(url)}</a>""")
                            append(escapeHtml(suffix))
                            index = end
                            continue
                        }
                    }

                    source.startsWith("***", index) || source.startsWith("___", index) -> {
                        val delimiter = source.substring(index, index + 3)
                        val end = source.indexOf(delimiter, startIndex = index + 3)
                        if (end != -1) {
                            append("<strong><em>")
                            append(parseSegment(source.substring(index + 3, end)))
                            append("</em></strong>")
                            index = end + 3
                            continue
                        }
                    }

                    source.startsWith("**", index) || source.startsWith("__", index) -> {
                        val delimiter = source.substring(index, index + 2)
                        val end = source.indexOf(delimiter, startIndex = index + 2)
                        if (end != -1) {
                            append(buildHtmlTag("strong", parseSegment(source.substring(index + 2, end))))
                            index = end + 2
                            continue
                        }
                    }

                    source.startsWith("~~", index) -> {
                        val end = source.indexOf("~~", startIndex = index + 2)
                        if (end != -1) {
                            append(buildHtmlTag("del", parseSegment(source.substring(index + 2, end))))
                            index = end + 2
                            continue
                        }
                    }

                    source.startsWith("==", index) -> {
                        val end = source.indexOf("==", startIndex = index + 2)
                        if (end != -1) {
                            append(buildHtmlTag("mark", parseSegment(source.substring(index + 2, end))))
                            index = end + 2
                            continue
                        }
                    }

                    source.startsWith("`", index) -> {
                        val end = source.indexOf('`', startIndex = index + 1)
                        if (end != -1) {
                            append(buildHtmlTag("code", escapeHtml(source.substring(index + 1, end))))
                            index = end + 1
                            continue
                        }
                    }

                    source.startsWith("[", index) -> {
                        val labelEnd = source.indexOf(']', startIndex = index + 1)
                        val urlStart = labelEnd.takeIf { it != -1 }?.plus(1)
                        if (
                            labelEnd != -1 &&
                            urlStart != null &&
                            urlStart < source.length &&
                            source[urlStart] == '('
                        ) {
                            val urlEnd = source.indexOf(')', startIndex = urlStart + 1)
                            if (urlEnd != -1) {
                                val label = parseSegment(
                                    source.substring(index + 1, labelEnd),
                                    allowAutolinks = false,
                                )
                                val url = escapeHtmlAttribute(source.substring(urlStart + 1, urlEnd))
                                append("""<a href="$url">$label</a>""")
                                index = urlEnd + 1
                                continue
                            }
                        }
                    }

                    source.startsWith("*", index) || source.startsWith("_", index) -> {
                        val delimiter = source[index]
                        val end = source.indexOf(delimiter, startIndex = index + 1)
                        if (end != -1) {
                            append(buildHtmlTag("em", parseSegment(source.substring(index + 1, end))))
                            index = end + 1
                            continue
                        }
                    }
                }

                append(escapeHtml(source[index].toString()))
                index += 1
            }
        }

    return text.split('\n').joinToString("<br />") { line ->
        parseSegment(line)
    }
}

private fun buildAiChatPlainTextHtml(text: String): String =
    text.split('\n').joinToString("<br />") { line ->
        escapeHtml(line)
    }

private fun buildHtmlTag(
    tag: String,
    content: String,
): String = "<$tag>$content</${tag.substringBefore(' ')}>"

private fun isTableHeaderLine(line: String): Boolean = line.contains('|')

private fun isTableBodyLine(line: String): Boolean =
    line.isNotBlank() && line.contains('|') && !tableSeparatorRegex.matches(line)

private fun splitTableRow(line: String): List<String> =
    line.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split('|')
        .map { it.trim() }

private fun escapeHtml(text: String): String = Entities.escape(text)

private fun escapeHtmlAttribute(text: String): String = Entities.escape(text)

private fun trimTrailingUrlPunctuation(candidate: String): String {
    var url = candidate
    while (url.isNotEmpty()) {
        val trailing = url.last()
        url =
            when (trailing) {
                ',', '.', ';', ':', '!', '?', '，', '。', '；', '：', '！', '？' ->
                    url.dropLast(1)

                ')', ']', '}', '）', '】', '》' ->
                    if (shouldTrimTrailingClosingDelimiter(url, trailing)) {
                        url.dropLast(1)
                    } else {
                        return url
                    }

                else -> return url
            }
    }
    return url
}

private fun shouldTrimTrailingClosingDelimiter(
    url: String,
    closingDelimiter: Char,
): Boolean {
    val openingDelimiter =
        when (closingDelimiter) {
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            '）' -> '（'
            '】' -> '【'
            '》' -> '《'
            else -> return false
        }
    return url.count { it == closingDelimiter } > url.count { it == openingDelimiter }
}

private fun findInlineUrlEnd(source: String, startIndex: Int): Int {
    var end = startIndex
    while (end < source.length && !source[end].isWhitespace() && source[end] != '<') {
        end += 1
    }
    return end
}
