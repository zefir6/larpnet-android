package pl.larpnet.android.domain.html

import androidx.compose.ui.text.AnnotatedString

/** Block-level pieces of a parsed Friendica post body, rendered by ui/common/HtmlContent.kt. */
sealed interface HtmlNode {
    data class Paragraph(val text: AnnotatedString) : HtmlNode
    data class Image(val url: String, val description: String?) : HtmlNode
    data class Blockquote(val children: List<HtmlNode>) : HtmlNode
    data class CodeBlock(val text: String) : HtmlNode
    data class ListBlock(val items: List<AnnotatedString>, val ordered: Boolean) : HtmlNode
}
