package pl.larpnet.android.domain.thread

import pl.larpnet.android.data.model.Status

/** A status plus its direct replies, recursively -- the tree Friendica's flat descendants list gets reshaped into. */
data class ThreadNode(
    val status: Status,
    val children: List<ThreadNode>,
)

/**
 * Friendica's GET /api/v1/statuses/{id}/context returns `descendants` as a flat list ordered
 * by uri-id across the *whole* subtree -- not a nested tree, and (confirmed in
 * Statuses/Context.php) a parent can appear later in that array than its own child. This
 * mirrors the reconstruction the sibling macOS app does client-side in threading.ts.
 *
 * Algorithm: repeatedly scan the remaining flat list, attaching any status whose parent is
 * already known; requeue the rest. A full pass with zero attachments means the remainder are
 * either orphaned (parent outside this context, shouldn't normally happen) or genuinely done.
 */
fun buildThreadTree(focus: Status, descendants: List<Status>): ThreadNode {
    val childrenByParentId = mutableMapOf<String, MutableList<Status>>()
    val remaining = ArrayDeque(descendants)
    val known = mutableSetOf(focus.id)

    // Stalled counts consecutive candidates re-queued with no progress; once it reaches the
    // full remaining size, one entire pass attached nothing, so whatever's left is orphaned
    // (its parent isn't in this context at all) and further looping can't help.
    var stalled = 0
    while (remaining.isNotEmpty() && stalled < remaining.size) {
        val candidate = remaining.removeFirst()
        val parentId = candidate.inReplyToId
        if (parentId != null && parentId in known) {
            childrenByParentId.getOrPut(parentId) { mutableListOf() }.add(candidate)
            known.add(candidate.id)
            stalled = 0
        } else {
            remaining.addLast(candidate)
            stalled++
        }
    }

    fun buildNode(status: Status): ThreadNode {
        val children = childrenByParentId[status.id]
            .orEmpty()
            .sortedBy { it.createdAt }
            .map(::buildNode)
        return ThreadNode(status, children)
    }

    return buildNode(focus)
}
