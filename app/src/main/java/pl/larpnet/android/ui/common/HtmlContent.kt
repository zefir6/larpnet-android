package pl.larpnet.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pl.larpnet.android.domain.html.HtmlNode

/**
 * Renders a parsed post body (see domain/html/HtmlParser.kt) as native Compose content.
 * Links inside paragraphs/list items are carried as [androidx.compose.ui.text.LinkAnnotation]
 * spans (added in HtmlParser's inline walk), which `Text` handles natively -- no manual tap
 * offset/URL lookup needed here.
 */
@Composable
fun HtmlContent(nodes: List<HtmlNode>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.forEach { node -> HtmlNodeView(node) }
    }
}

@Composable
private fun HtmlNodeView(node: HtmlNode) {
    when (node) {
        is HtmlNode.Paragraph -> Text(
            text = node.text,
            style = MaterialTheme.typography.bodyLarge,
        )

        is HtmlNode.Image -> AsyncImage(
            model = node.url,
            contentDescription = node.description,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
        )

        is HtmlNode.Blockquote -> Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            node.children.forEach { HtmlNodeView(it) }
        }

        is HtmlNode.CodeBlock -> Text(
            text = node.text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(8.dp),
        )

        is HtmlNode.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            node.items.forEachIndexed { index, item ->
                Row {
                    Text(
                        text = if (node.ordered) "${index + 1}." else "•",
                        modifier = Modifier.padding(end = 8.dp),
                        color = Color.Unspecified,
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
