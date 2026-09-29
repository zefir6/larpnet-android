package pl.larpnet.android.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.larpnet.android.data.auth.TokenStore

/**
 * Persisted, user-customizable split of every [AppDestination.customizable] entry between three
 * zones: the bottom bar, the top-bar dropdown menu, and the "More" tab. iOS equivalent:
 * `NavigationLayoutStore.swift`. Shared between `NavGraph`'s bottom bar/top-bar menu and
 * `SettingsScreen`'s reorder controls via one [pl.larpnet.android.di.AppContainer] instance.
 *
 * [AppDestination.PROFILE] participates in this zone system (it can live in any of the three,
 * same as everything else) but is *not* given the same save/restore-state navigation the other
 * destinations get when clicked from a zone -- `ProfileScreen` deliberately reloads on every
 * visit (see its own doc comment), and save/restore-state would keep the composable alive across
 * visits and silently break that. `NavGraph` special-cases it at the call site, not here.
 */
class NavigationLayoutStore(private val tokenStore: TokenStore) {
    enum class Zone { BOTTOM_BAR, TOP_BAR, MORE }

    private val _bottomBar = MutableStateFlow<List<AppDestination>>(emptyList())
    val bottomBar: StateFlow<List<AppDestination>> = _bottomBar.asStateFlow()

    private val _topBar = MutableStateFlow<List<AppDestination>>(emptyList())
    val topBar: StateFlow<List<AppDestination>> = _topBar.asStateFlow()

    private val _more = MutableStateFlow<List<AppDestination>>(emptyList())
    val more: StateFlow<List<AppDestination>> = _more.asStateFlow()

    companion object {
        /** The UI hides moving a destination out of the bar once only this many remain, so the
         * bar never looks empty/broken. */
        const val MINIMUM_BOTTOM_BAR_COUNT = 2

        /** Not a hard platform constraint the way it is on iOS (Android's `NavigationBar`
         * doesn't auto-collapse into a nested overflow the way `UITabBarController` does) -- this
         * is purely to keep icons from getting visually cramped once the fixed "More" tab is
         * added alongside the bar. Matches iOS's default anyway, for one shared cross-platform
         * story. */
        const val MAXIMUM_BOTTOM_BAR_COUNT = 4

        val DEFAULT_BOTTOM_BAR = listOf(AppDestination.LOCAL, AppDestination.HOME, AppDestination.CHAT, AppDestination.DIRECTORY)
        val DEFAULT_TOP_BAR = listOf(AppDestination.PROFILE, AppDestination.SETTINGS)
        val DEFAULT_MORE = listOf(AppDestination.ALBUMS, AppDestination.MEDIA)
    }

    init {
        val (bar, top, more) = readPersisted()
        _bottomBar.value = bar
        _topBar.value = top
        _more.value = more
    }

    /** Reorder only, membership unchanged -- [newOrder] must be an exact permutation of the
     * zone's current contents. Use [move] to change which zone a destination lives in. */
    fun setBottomBar(newOrder: List<AppDestination>) {
        if (newOrder.toSet() != _bottomBar.value.toSet() || newOrder.size != _bottomBar.value.size) return
        _bottomBar.value = newOrder
        persist()
    }

    fun setTopBar(newOrder: List<AppDestination>) {
        if (newOrder.toSet() != _topBar.value.toSet() || newOrder.size != _topBar.value.size) return
        _topBar.value = newOrder
        persist()
    }

    fun setMore(newOrder: List<AppDestination>) {
        if (newOrder.toSet() != _more.value.toSet() || newOrder.size != _more.value.size) return
        _more.value = newOrder
        persist()
    }

    /** Moves [destination] into [zone], removing it from whichever zone currently holds it.
     * No-op if this would drop the bottom bar below [MINIMUM_BOTTOM_BAR_COUNT] or grow it past
     * [MAXIMUM_BOTTOM_BAR_COUNT] -- callers (`SettingsScreen`'s per-row menu) already hide that
     * option once a limit is reached, this is just the safety net. */
    fun move(destination: AppDestination, zone: Zone) {
        if (_bottomBar.value.contains(destination) && zone != Zone.BOTTOM_BAR && _bottomBar.value.size <= MINIMUM_BOTTOM_BAR_COUNT) {
            return
        }
        if (zone == Zone.BOTTOM_BAR && !_bottomBar.value.contains(destination) && _bottomBar.value.size >= MAXIMUM_BOTTOM_BAR_COUNT) {
            return
        }
        _bottomBar.value = _bottomBar.value.filterNot { it == destination }
        _topBar.value = _topBar.value.filterNot { it == destination }
        _more.value = _more.value.filterNot { it == destination }
        when (zone) {
            Zone.BOTTOM_BAR -> _bottomBar.value = _bottomBar.value + destination
            Zone.TOP_BAR -> _topBar.value = _topBar.value + destination
            Zone.MORE -> _more.value = _more.value + destination
        }
        persist()
    }

    private fun persist() {
        tokenStore.navBottomBar = _bottomBar.value.joinToString(",") { it.name }
        tokenStore.navTopBar = _topBar.value.joinToString(",") { it.name }
        tokenStore.navMore = _more.value.joinToString(",") { it.name }
    }

    private fun readPersisted(): Triple<List<AppDestination>, List<AppDestination>, List<AppDestination>> {
        val bar = tokenStore.navBottomBar?.let(::parse)
        val top = tokenStore.navTopBar?.let(::parse)
        val more = tokenStore.navMore?.let(::parse)
        if (bar != null && top != null && more != null && isValidPartition(bar, top, more)) {
            return Triple(bar, top, more)
        }
        return Triple(DEFAULT_BOTTOM_BAR, DEFAULT_TOP_BAR, DEFAULT_MORE)
    }

    private fun parse(raw: String): List<AppDestination> =
        raw.split(",").mapNotNull { name -> runCatching { AppDestination.valueOf(name) }.getOrNull() }

    private fun isValidPartition(bar: List<AppDestination>, top: List<AppDestination>, more: List<AppDestination>): Boolean {
        val all = AppDestination.customizable.toSet()
        val union = (bar + top + more).toSet()
        return union == all && bar.size + top.size + more.size == all.size
    }
}
