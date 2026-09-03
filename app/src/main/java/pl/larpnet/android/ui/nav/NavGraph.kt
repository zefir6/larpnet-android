package pl.larpnet.android.ui.nav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import androidx.compose.runtime.collectAsState
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
import pl.larpnet.android.BuildConfig
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.compose.ComposeScreen
import pl.larpnet.android.ui.directory.DirectoryScreen
import pl.larpnet.android.ui.login.LoginScreen
import pl.larpnet.android.ui.messages.ConversationThreadScreen
import pl.larpnet.android.ui.messages.ConversationsScreen
import pl.larpnet.android.ui.notifications.NotificationsScreen
import pl.larpnet.android.push.PushControl
import pl.larpnet.android.ui.common.UpdateBanner
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
    const val MESSAGES = "messages"
    const val NEW_MESSAGE = "messages/new"
    const val MESSAGE_THREAD = "messages/thread/{accountId}?conversationId={conversationId}"
}

private val bottomNavRoutes = setOf(Routes.HOME, Routes.LOCAL, Routes.DIRECTORY, Routes.NOTIFICATIONS, Routes.SETTINGS)

private fun threadRoute(statusId: String) = "thread/$statusId"
private fun profileRoute(accountId: String) = "profile/$accountId"
private fun composeRoute(replyToId: String? = null) = if (replyToId != null) "compose?replyToId=$replyToId" else "compose"
private fun messageThreadRoute(accountId: String, conversationId: String? = null) =
    if (conversationId != null) "messages/thread/$accountId?conversationId=$conversationId" else "messages/thread/$accountId"

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
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Result doesn't change what we do -- push starts either way, see PushControl. */ }

    fun startPushIfEnabled() {
        if (!appContainer.tokenStore.isLoggedIn || !appContainer.tokenStore.pushEnabled) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Battery-exemption nagging is specific to NtfyListenerService's persistent socket --
        // FCM (Play Store build, BuildConfig.FCM_PUSH_ENABLED) is OS-scheduled delivery, no
        // always-on connection to protect from the battery manager.
        if (!BuildConfig.FCM_PUSH_ENABLED) {
            // Re-check on every cold start, not just when the user flips the Settings switch:
            // this covers installs where push was already enabled before battery-exemption
            // requesting existed, and OEMs (MIUI in particular) that silently revoke the
            // exemption behind the user's back. No-ops instantly if already exempted, so it's
            // not naggy for anyone it already worked for. See SettingsScreen.setPushEnabled.
            val powerManager = context.getSystemService(PowerManager::class.java)
            if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }
        PushControl.start(context, appContainer)
    }

    // Resumes the push listener across process restarts while already logged in -- the actual
    // fresh-login case is handled by the LOGIN composable's onLoggedIn below, since this effect
    // only runs once for this NavGraph instance's lifetime and won't re-fire after that navigate.
    LaunchedEffect(Unit) { startPushIfEnabled() }

    // GitHub-distributed builds have to notice their own updates -- see UpdateRepository.
    // Throttled internally to at most once/24h; the banner below reflects whatever it finds,
    // and Settings' manual "Check for updates" shares the same state. Play Store builds skip
    // this entirely (BuildConfig.UPDATE_CHECK_ENABLED is false there) -- Play handles updates.
    if (BuildConfig.UPDATE_CHECK_ENABLED) {
        LaunchedEffect(Unit) { appContainer.updateRepository.checkIfDue() }
    }
    val availableUpdate by appContainer.updateRepository.updateAvailable.collectAsState()

    LaunchedEffect(Unit) {
        appContainer.authInterceptor.forceLogoutEvents.collect {
            PushControl.stop(context, appContainer)
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
        Column(modifier = Modifier.padding(padding)) {
            availableUpdate?.let { update ->
                UpdateBanner(
                    update = update,
                    onDownload = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
                    },
                    onDismiss = { appContainer.updateRepository.dismiss() },
                )
            }
            NavHost(
                navController = navController,
                startDestination = startDestination,
            ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = {
                        startPushIfEnabled()
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
                    onOpenMessages = { navController.navigate(Routes.MESSAGES) },
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

            composable(Routes.MESSAGES) {
                ConversationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenConversation = { conversation ->
                        val accountId = conversation.accounts.firstOrNull()?.id ?: return@ConversationsScreen
                        navController.navigate(messageThreadRoute(accountId, conversation.id))
                    },
                    onNewMessage = { navController.navigate(Routes.NEW_MESSAGE) },
                )
            }

            composable(Routes.NEW_MESSAGE) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = onOpenProfile,
                    onSelectAccount = { account ->
                        navController.navigate(messageThreadRoute(account.id)) {
                            popUpTo(Routes.MESSAGES)
                        }
                    },
                )
            }

            composable(
                Routes.MESSAGE_THREAD,
                arguments = listOf(
                    navArgument("accountId") { type = NavType.StringType },
                    navArgument("conversationId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val accountId = entry.arguments?.getString("accountId").orEmpty()
                val conversationId = entry.arguments?.getString("conversationId")
                ConversationThreadScreen(
                    accountId = accountId,
                    conversationId = conversationId,
                    onBack = { navController.popBackStack() },
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
}
