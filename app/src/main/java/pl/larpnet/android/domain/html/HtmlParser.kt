package pl.larpnet.android.domain.html

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Safelist

/**
 * Parses Friendica's raw HTML post `content` into a small block-level tree the UI renders
 * natively (see ui/common/HtmlContent.kt). Friendica posts are BBCode-derived HTML --
 * images, blockquotes, lists, code blocks -- richer than plain Mastodon text, so a plain
 * HtmlCompat.fromHtml()-to-plain-text pass would silently drop images and mangle structure,
 * and a WebView per timeline row doesn't scale.
 *
 * The allow-list below is a direct analogue of the sibling macOS app's sanitize.ts DOMPurify
 * config: only content-bearing tags survive, no script/style/event handlers/inline styles.
 */
object HtmlParser {

    private val safelist: Safelist = Safelist.none()
        .addTags(
            "p", "br", "a", "span", "strong", "b", "em", "i", "u", "s", "del",
            "ul", "ol", "li", "blockquote", "code", "pre", "img", "h1", "h2", "h3", "h4",
        )
        .addAttributes("a", "href")
        .addAttributes("img", "src", "alt")
        .addProtocols("a", "href", "http", "https")
        .addProtocols("img", "src", "http", "https")

    /**
     * Real posts pulled from the public timeline (federated Fediverse content, not just
     * Friendica-authored ones) confirmed this matters: short posts -- e.g. a caption that's
     * just a run of `#hashtag` links -- often aren't wrapped in a `<p>` at all; the `<a>` tags
     * and bare text sit directly as children of the body. Treating every top-level node as its
     * own block (the previous approach) rendered one hashtag per line instead of one flowing
     * paragraph. This tag set defines what actually starts a new block; anything else
     * (text, `<a>`, `<span>`, `<strong>`, etc.) is inline and gets accumulated into the
     * current paragraph until a real block tag or the end of input flushes it.
     */
    private val blockTags = setOf("p", "h1", "h2", "h3", "h4", "blockquote", "pre", "code", "ul", "ol", "img")

    fun parse(html: String): List<HtmlNode> {
        if (html.isBlank()) return emptyList()
        val cleaned = Jsoup.clean(html, "", safelist)
        val body = Jsoup.parseBodyFragment(cleaned).body()
        return parseSiblings(body.childNodes())
    }

    private fun parseSiblings(nodes: List<Node>): List<HtmlNode> {
        val result = mutableListOf<HtmlNode>()
        val inlineBuffer = mutableListOf<Node>()

        fun flushInlineBuffer() {
            if (inlineBuffer.isEmpty()) return
            val text = inlineNodesToAnnotatedString(inlineBuffer)
            if (text.text.isNotBlank()) result.add(HtmlNode.Paragraph(text))
            inlineBuffer.clear()
        }

        for (node in nodes) {
            if (node is Element && node.tagName() in blockTags) {
                flushInlineBuffer()
                result.addAll(parseBlockElement(node))
            } else {
                inlineBuffer.add(node)
            }
        }
        flushInlineBuffer()
        return result
    }

    private fun parseBlockElement(element: Element): List<HtmlNode> {
        return when (element.tagName()) {
            "p", "h1", "h2", "h3", "h4" -> {
                val text = inlineNodesToAnnotatedString(element.childNodes())
                if (text.text.isBlank()) emptyList() else listOf(HtmlNode.Paragraph(text))
            }
            "img" -> {
                val src = element.attr("src")
                if (src.isBlank()) emptyList() else listOf(HtmlNode.Image(src, element.attr("alt").ifBlank { null }))
            }
            "blockquote" -> listOf(HtmlNode.Blockquote(parseSiblings(element.childNodes())))
            "pre", "code" -> listOf(HtmlNode.CodeBlock(element.text()))
            "ul", "ol" -> {
                val items = element.children()
                    .filter { it.tagName() == "li" }
                    .map { inlineNodesToAnnotatedString(it.childNodes()) }
                listOf(HtmlNode.ListBlock(items, ordered = element.tagName() == "ol"))
            }
            else -> parseSiblings(element.childNodes())
        }
    }

    private fun inlineNodesToAnnotatedString(nodes: List<Node>): AnnotatedString = buildAnnotatedString {
        fun walk(node: Node) {
            when (node) {
                is TextNode -> append(node.text())
                is Element -> when (node.tagName()) {
                    "br" -> append('\n')
                    "strong", "b" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        node.childNodes().forEach(::walk)
                    }
                    "em", "i" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        node.childNodes().forEach(::walk)
                    }
                    "u" -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        node.childNodes().forEach(::walk)
                    }
                    "s", "del" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        node.childNodes().forEach(::walk)
                    }
                    "a" -> {
                        val href = node.attr("href")
                        val linkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
                        withLink(LinkAnnotation.Url(href, linkStyles)) {
                            node.childNodes().forEach(::walk)
                        }
                    }
                    else -> node.childNodes().forEach(::walk)
                }
                else -> Unit
            }
        }
        nodes.forEach(::walk)
    }
}
