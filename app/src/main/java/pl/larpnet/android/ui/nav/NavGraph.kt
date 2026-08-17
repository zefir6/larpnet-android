package pl.larpnet.android.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.compose.ComposeScreen
import pl.larpnet.android.ui.directory.DirectoryScreen
import pl.larpnet.android.ui.login.LoginScreen
import pl.larpnet.android.ui.notifications.NotificationsScreen
import pl.larpnet.android.ui.profile.EditProfileScreen
import pl.larpnet.android.ui.profile.ProfileScreen
import pl.larpnet.android.ui.search.SearchScreen
import pl.larpnet.android.ui.settings.SettingsScreen
import pl.larpnet.android.ui.thread.ThreadScreen
import pl.larpnet.android.ui.timeline.TimelineKind
import pl.larpnet.android.ui.timeline.TimelineScreen

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val LOCAL = "local"
    const val DIRECTORY = "directory"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val THREAD = "thread/{statusId}"
    const val OTHER_PROFILE = "profile/{accountId}"
    const val EDIT_PROFILE = "edit_profile"
    const val SETTINGS = "settings"
    const val COMPOSE = "compose?replyToId={replyToId}"
    const val SEARCH = "search"
}

private val bottomNavRoutes = setOf(Routes.HOME, Routes.LOCAL, Routes.DIRECTORY, Routes.NOTIFICATIONS, Routes.SETTINGS)

private fun threadRoute(statusId: String) = "thread/$statusId"
private fun profileRoute(accountId: String) = "profile/$accountId"
private fun composeRoute(replyToId: String? = null) = if (replyToId != null) "compose?replyToId=$replyToId" else "compose"

private fun NavHostController.navigateToBottomTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** [startDestination] is [Routes.LOGIN] or [Routes.HOME] depending on whether MainActivity found a stored token. */
@Composable
fun LarpnetNavGraph(startDestination: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // A revoked/invalid token surfaces as 401/403 from any call; AuthInterceptor can't
    // navigate itself (it runs on an OkHttp thread), so it emits an event this collects to
    // clear the stored token and bounce back to login instead of leaving screens stuck
    // showing stale data or silent failures.
    val appContainer = rememberAppContainer()
    LaunchedEffect(Unit) {
        appContainer.authInterceptor.forceLogoutEvents.collect {
            appContainer.tokenStore.clear()
            if (currentRoute != Routes.LOGIN) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val onOpenThread: (Status) -> Unit = { navController.navigate(threadRoute(it.id)) }
    val onOpenProfile: (String) -> Unit = { navController.navigate(profileRoute(it)) }
    val onReply: (Status) -> Unit = { navController.navigate(composeRoute(it.id)) }
    val onSearch: () -> Unit = { navController.navigate(Routes.SEARCH) }

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = { navController.navigateToBottomTab(Routes.HOME) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.LOCAL,
                        onClick = { navController.navigateToBottomTab(Routes.LOCAL) },
                        icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_local)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.DIRECTORY,
                        onClick = { navController.navigateToBottomTab(Routes.DIRECTORY) },
                        icon = { Icon(Icons.Filled.PeopleAlt, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_directory)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.NOTIFICATIONS,
                        onClick = { navController.navigateToBottomTab(Routes.NOTIFICATIONS) },
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_notifications)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigateToBottomTab(Routes.SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.settings_title)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.HOME || currentRoute == Routes.LOCAL) {
                FloatingActionButton(onClick = { navController.navigate(composeRoute()) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.compose_title_new))
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.HOME) {
                TimelineScreen(TimelineKind.Home, onOpenThread, onOpenProfile, onReply, onSearch)
            }

            composable(Routes.LOCAL) {
                TimelineScreen(TimelineKind.Local, onOpenThread, onOpenProfile, onReply, onSearch)
            }

            composable(Routes.DIRECTORY) {
                DirectoryScreen(onOpenProfile = onOpenProfile)
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onOpenStatus = { navController.navigate(threadRoute(it)) },
                    onOpenProfile = onOpenProfile,
                    onSearch = onSearch,
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    accountId = null,
                    onBack = { navController.popBackStack() },
                    onOpenThread = onOpenThread,
                    onOpenProfile = onOpenProfile,
                    onReply = onReply,
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                    onSearch = onSearch,
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = onOpenProfile,
                )
            }

            composable(
                Routes.THREAD,
                arguments = listOf(navArgument("statusId") { type = NavType.StringType }),
            ) { entry ->
                val statusId = entry.arguments?.getString("statusId").orEmpty()
                ThreadScreen(
                    statusId = statusId,
                    onBack = { navController.popBackStack() },
                    onOpenThread = onOpenThread,
                    onOpenProfile = onOpenProfile,
                    onReply = onReply,
                )
            }

            composable(
                Routes.OTHER_PROFILE,
                arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
            ) { entry ->
                val accountId = entry.arguments?.getString("accountId")
                ProfileScreen(
                    accountId = accountId,
                    onBack = { navController.popBackStack() },
                    onOpenThread = onOpenThread,
                    onOpenProfile = onOpenProfile,
                    onReply = onReply,
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                    onSearch = onSearch,
                )
            }

            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = null,
                    onOpenProfile = { navController.navigate(Routes.PROFILE) },
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                Routes.COMPOSE,
                arguments = listOf(
                    navArgument("replyToId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                ComposeScreen(
                    replyToId = entry.arguments?.getString("replyToId"),
                    onBack = { navController.popBackStack() },
                    onPosted = { navController.popBackStack() },
                )
            }
        }
    }
}
