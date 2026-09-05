package pl.larpnet.android.ui.compose

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.larpnet.android.data.auth.TokenStore

/**
 * Last-3 hashtags the user has published with, most-recent-first, offered as quick toggles
 * alongside the predefined tags in [TagsSection]. Persisted as a comma-separated list in
 * [TokenStore.recentTags], mirroring [pl.larpnet.android.ui.nav.BottomNavOrderStore]'s pattern.
 */
class RecentTagsStore(private val tokenStore: TokenStore) {
    private val _recentTags = MutableStateFlow(readPersisted())
    val recentTags: StateFlow<List<String>> = _recentTags.asStateFlow()

    fun recordUsed(tag: String) {
        val updated = (listOf(tag) + _recentTags.value.filterNot { it.equals(tag, ignoreCase = true) })
            .take(MAX_RECENT)
        tokenStore.recentTags = updated.joinToString(",")
        _recentTags.value = updated
    }

    private fun readPersisted(): List<String> =
        tokenStore.recentTags?.split(",")?.filter { it.isNotBlank() }?.take(MAX_RECENT) ?: emptyList()

    companion object {
        private const val MAX_RECENT = 3
    }
}
