package pl.larpnet.android.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import pl.larpnet.android.R

/**
 * Every top-level screen reachable from the bottom bar, the top-bar dropdown, or the "More" tab
 * -- see [NavigationLayoutStore] for how each one is assigned to a zone. iOS equivalent:
 * `AppDestination.swift`. Deliberately smaller than iOS's set -- there's no Android Contacts
 * screen -- and [PROFILE] behaves differently from the rest (see [NavigationLayoutStore]'s doc
 * comment on why it isn't routed through the tab-style save/restore-state navigation the others
 * use).
 */
enum class AppDestination(val route: String, val icon: ImageVector, val labelRes: Int) {
    HOME(Routes.HOME, Icons.Filled.Home, R.string.nav_home),
    LOCAL(Routes.LOCAL, Icons.Filled.Groups, R.string.nav_local),
    DIRECTORY(Routes.DIRECTORY, Icons.Filled.PeopleAlt, R.string.nav_directory),
    NOTIFICATIONS(Routes.NOTIFICATIONS, Icons.Filled.Notifications, R.string.nav_notifications),
    SETTINGS(Routes.SETTINGS, Icons.Filled.Settings, R.string.settings_title),
    PROFILE(Routes.PROFILE, Icons.Filled.Person, R.string.nav_profile),
    ALBUMS(Routes.ALBUMS, Icons.Filled.PhotoLibrary, R.string.albums_title),
    MEDIA(Routes.MEDIA, Icons.Filled.Collections, R.string.media_title),
    CHAT(Routes.CHAT, Icons.AutoMirrored.Filled.Chat, R.string.chat_title),
    ;

    companion object {
        /** [NOTIFICATIONS] is a fixed top-right icon on every screen (see `TopBarActions.kt`),
         * not something the user places into a zone -- same exclusion as iOS's
         * `AppDestination.customizableCases`. */
        val customizable: List<AppDestination> = entries.filter { it != NOTIFICATIONS }
    }
}
