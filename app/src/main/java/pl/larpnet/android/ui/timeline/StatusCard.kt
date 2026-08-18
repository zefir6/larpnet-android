package pl.larpnet.android.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.domain.html.HtmlParser
import pl.larpnet.android.ui.common.AvatarImage
import pl.larpnet.android.ui.common.HtmlContent
import pl.larpnet.android.ui.common.RelativeTime
import pl.larpnet.android.ui.common.VisibilityIcon
import pl.larpnet.android.ui.theme.larpnetCard

/**
 * Renders one post (or a boost of one). [status] is always the top-level API object; when it's
 * a reblog, the boosted-by banner refers to [status.account] while everything else (body,
 * actions, etc.) operates on the boosted post ([Status.reblog]) -- same distinction Mastodon
 * clients make.
 *
 * Content-warning collapsing only triggers on [Status.sensitive] == true; [Status.spoilerText]
 * is frequently just the post's plain subject line on this server (see data/model/Status.kt),
 * so it's always shown as a heading rather than treated as an implicit "collapse" flag.
 */
@Composable
fun StatusCard(
    status: Status,
    onOpenThread: (Status) -> Unit,
    onOpenProfile: (String) -> Unit,
    onReply: (Status) -> Unit,
    onToggleFavourite: (Status) -> Unit,
    onToggleReblog: (Status) -> Unit,
    onToggleBookmark: (Status) -> Unit,
    onDelete: ((Status) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val display = status.reblog ?: status
    var contentVisible by remember(display.id) { mutableStateOf(!display.sensitive) }
    val htmlNodes = remember(display.content) { HtmlParser.parse(display.content) }
    var showDeleteConfirm by remember(display.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .larpnetCard()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onOpenThread(display) }
            .padding(16.dp),
    ) {
        if (status.reblog != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = status.account.displayName.ifBlank { status.account.username },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.Top) {
            AvatarImage(
                url = display.account.avatar,
                contentDescription = display.account.displayName,
                modifier = Modifier.clickable { onOpenProfile(display.account.id) },
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = display.account.displayName.ifBlank { display.account.username },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "@${display.account.acct}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VisibilityIcon(display.visibility, modifier = Modifier.size(16.dp).padding(top = 4.dp))
            RelativeTime(display.createdAt, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            if (onDelete != null) {
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (display.spoilerText.isNotBlank()) {
            Text(
                text = display.spoilerText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (display.sensitive && !contentVisible) {
            TextButton(onClick = { contentVisible = true }) {
                Text(stringResource(R.string.content_warning_show))
            }
        } else {
            HtmlContent(htmlNodes, modifier = Modifier.padding(top = 8.dp))

            if (display.mediaAttachments.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(display.mediaAttachments) { media ->
                        AsyncImage(
                            model = media.previewUrl ?: media.url,
                            contentDescription = media.description,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }

            if (display.sensitive) {
                TextButton(onClick = { contentVisible = false }) {
                    Text(stringResource(R.string.content_warning_hide))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ActionButton(
                icon = Icons.Filled.ChatBubbleOutline,
                count = display.repliesCount,
                contentDescription = stringResource(R.string.action_reply),
                onClick = { onReply(display) },
            )
            ActionButton(
                icon = Icons.Filled.Repeat,
                count = display.reblogsCount,
                contentDescription = stringResource(
                    if (display.reblogged) R.string.action_unreblog else R.string.action_reblog,
                ),
                tinted = display.reblogged,
                onClick = { onToggleReblog(display) },
            )
            ActionButton(
                icon = if (display.favourited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                count = display.favouritesCount,
                contentDescription = stringResource(
                    if (display.favourited) R.string.action_unfavourite else R.string.action_favourite,
                ),
                tinted = display.favourited,
                onClick = { onToggleFavourite(display) },
            )
            IconButton(onClick = { onToggleBookmark(display) }) {
                Icon(
                    imageVector = if (display.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = stringResource(
                        if (display.bookmarked) R.string.action_unbookmark else R.string.action_bookmark,
                    ),
                    tint = if (display.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete(display) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Long,
    contentDescription: String,
    onClick: () -> Unit,
    tinted: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (tinted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(24.dp),
            )
        }
    }
}
