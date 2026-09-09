package pl.larpnet.android.ui.moderation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.larpnet.android.data.auth.TokenStore

/**
 * A client-side-only list of status ids excluded from timelines/threads on this device -- no
 * server-side concept, distinct from blocking an *account* (see FriendicaApi.block). Two
 * instances are wired in AppContainer, one per [TokenStore.hiddenPostIds]/[TokenStore.blockedPostIds],
 * mirroring [pl.larpnet.android.ui.nav.BottomNavOrderStore]'s StateFlow-over-TokenStore pattern.
 */
class LocalPostFilterStore(
    private val tokenStore: TokenStore,
    private val read: (TokenStore) -> String?,
    private val write: (TokenStore, String?) -> Unit,
) {
    private val _ids = MutableStateFlow(readPersisted())
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    fun add(id: String) {
        if (id in _ids.value) return
        val updated = _ids.value + id
        write(tokenStore, updated.joinToString(","))
        _ids.value = updated
    }

    fun remove(id: String) {
        val updated = _ids.value - id
        write(tokenStore, if (updated.isEmpty()) null else updated.joinToString(","))
        _ids.value = updated
    }

    private fun readPersisted(): List<String> =
        read(tokenStore)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
}
