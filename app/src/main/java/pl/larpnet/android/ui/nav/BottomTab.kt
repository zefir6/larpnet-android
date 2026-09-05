package pl.larpnet.android.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.larpnet.android.R
import pl.larpnet.android.data.auth.TokenStore

/** One tab in the bottom [androidx.compose.material3.NavigationBar]. */
enum class BottomTab(val route: String, val icon: ImageVector, val labelRes: Int) {
    HOME(Routes.HOME, Icons.Filled.Home, R.string.nav_home),
    LOCAL(Routes.LOCAL, Icons.Filled.Groups, R.string.nav_local),
    DIRECTORY(Routes.DIRECTORY, Icons.Filled.PeopleAlt, R.string.nav_directory),
    NOTIFICATIONS(Routes.NOTIFICATIONS, Icons.Filled.Notifications, R.string.nav_notifications),
    SETTINGS(Routes.SETTINGS, Icons.Filled.Settings, R.string.settings_title),
}

/** LARPnet (the local timeline) is the app's namesake feed, so it leads ahead of the account's own home timeline. */
val defaultBottomTabOrder = listOf(BottomTab.LOCAL, BottomTab.HOME, BottomTab.DIRECTORY, BottomTab.NOTIFICATIONS, BottomTab.SETTINGS)

/**
 * User-customizable ordering of [BottomTab]s (Settings > bottom bar order), persisted as a
 * comma-separated list of enum names in [TokenStore.bottomNavOrder]. Backed by a StateFlow
 * (rather than reading TokenStore directly) so NavGraph's bottom bar and SettingsScreen's
 * reorder controls -- two different composables sharing one [pl.larpnet.android.di.AppContainer]
 * instance -- stay in sync live, without needing to navigate away and back.
 */
class BottomNavOrderStore(private val tokenStore: TokenStore) {
    private val _order = MutableStateFlow(readPersisted())
    val order: StateFlow<List<BottomTab>> = _order.asStateFlow()

    /** [newOrder] must be a permutation of all [BottomTab.entries] -- this reorders tabs, it doesn't hide them. */
    fun setOrder(newOrder: List<BottomTab>) {
        require(newOrder.toSet() == BottomTab.entries.toSet()) { "newOrder must contain every BottomTab exactly once" }
        tokenStore.bottomNavOrder = newOrder.joinToString(",") { it.name }
        _order.value = newOrder
    }

    /** Falls back to appending anything missing from a partial/corrupt/outdated stored value, rather than
     * crashing or silently dropping a tab (e.g. a future app version adds a new [BottomTab]). */
    private fun readPersisted(): List<BottomTab> {
        val stored = tokenStore.bottomNavOrder?.split(",")?.mapNotNull { name ->
            runCatching { BottomTab.valueOf(name) }.getOrNull()
        }?.distinct()
        if (stored.isNullOrEmpty()) return defaultBottomTabOrder
        val missing = BottomTab.entries.filter { it !in stored }
        return stored + missing
    }
}
