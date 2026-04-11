package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleContentBlockParserTest {

    @Test
    fun parsesSupportedBlocksIntoStableOrderedSequence() {
        val html =
            """
            <div>
              <h2>Heading</h2>
              <p>First paragraph.</p>
              <blockquote>Quoted text</blockquote>
              <ul><li>Bullet one</li><li>Bullet two</li></ul>
              <pre><code>println("hi")</code></pre>
              <img src="https://example.com/image.jpg" alt="cover" />
            </div>
            """.trimIndent()

        val blocks = ArticleContentBlockParser.parse(content = html, baseUrl = "https://example.com")

        assertEquals(
            listOf(
                ArticleContentBlockType.Heading,
                ArticleContentBlockType.Paragraph,
                ArticleContentBlockType.Quote,
                ArticleContentBlockType.ListItem,
                ArticleContentBlockType.ListItem,
                ArticleContentBlockType.CodeBlock,
                ArticleContentBlockType.Image,
            ),
            blocks.map { it.type },
        )
        assertEquals(
            listOf("heading_1", "paragraph_1", "quote_1", "list_item_1", "list_item_2"),
            blocks.filter { it.isTranslationEligible }.map { it.id },
        )
        assertEquals(
            listOf("Heading", "First paragraph.", "Quoted text", "Bullet one", "Bullet two"),
            blocks.filter { it.isTranslationEligible }.map { it.translationText },
        )
        assertTrue(blocks.last().originalHtml.contains("image.jpg"))
    }

    @Test
    fun translationSourceHashIsStableForEquivalentContent() {
        val html =
            """
            <div>
              <p>Alpha</p>
              <p>Beta</p>
            </div>
            """.trimIndent()

        val first = ArticleContentBlockParser.parse(html, "https://example.com")
        val second = ArticleContentBlockParser.parse(html, "https://example.com")

        assertEquals(
            ArticleContentBlockParser.translationSourceHash(first),
            ArticleContentBlockParser.translationSourceHash(second),
        )
    }

    @Test
    fun translationSourceHashChangesWhenTranslatableTextChanges() {
        val original = ArticleContentBlockParser.parse("<p>Alpha</p>", "https://example.com")
        val updated = ArticleContentBlockParser.parse("<p>Gamma</p>", "https://example.com")

        assertNotEquals(
            ArticleContentBlockParser.translationSourceHash(original),
            ArticleContentBlockParser.translationSourceHash(updated),
        )
    }
}
