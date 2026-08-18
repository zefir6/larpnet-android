package pl.larpnet.android.ui.settings

import android.Manifest
import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import pl.larpnet.android.R
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.data.repository.AuthRepository
import pl.larpnet.android.data.repository.ProfileRepository
import pl.larpnet.android.data.repository.PushRepository
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.push.NtfyListenerService
import pl.larpnet.android.ui.common.AvatarImage

/**
 * The Mastodon-compatible API only exposes a handful of Friendica's web profile settings
 * (display name, bio, plus the three switches below, all via `update_credentials`). Avatar
 * and header photo uploads are NOT reachable here despite `update_credentials` accepting
 * `avatar`/`header` params: that endpoint is PATCH-only (static/routes.config.php) and PHP
 * never populates $_FILES for a PATCH body, so Photo::uploadAvatar's `!empty($files)` guard
 * is always false server-side -- confirmed live (contact photo GUID unchanged after upload).
 *
 * Custom profile fields, birthday, and interface language have NO write path anywhere on
 * this server -- not just on the Mastodon layer. Checked every API surface Friendica exposes:
 * `update_credentials`'s `fields_attributes` param is accepted and never read; no `dob` field
 * exists anywhere in the Mastodon Account object or its update path; `GET /api/v1/preferences`
 * is GET-only (routes.config.php) and is the one place language is genuinely readable, but
 * nothing writes it -- `Source.language` in the verify_credentials response is hardcoded to
 * `''` and never wired up. Account language is therefore not shown in this screen at all
 * (only the [App language][settings_app_language_section] picker, which is app-local and has
 * nothing to do with this); its hint text points users to the website instead. The
 * Twitter-compatible
 * legacy endpoints (`account/update_profile`, `profile/show`) don't cover them either. All
 * three, plus everything else on /settings/profile and the rest of /settings (2FA, connected
 * apps, addons, data export, delegation, ...), live behind session-cookie HTML forms with no
 * JSON API -- the only way to reach any of it from here is linking out, see [settings_open_web].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null, onOpenProfile: () -> Unit, onLoggedOut: () -> Unit) {
    val appContainer = rememberAppContainer()
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    appContainer.profileRepository,
                    appContainer.authRepository,
                    appContainer.tokenStore,
                    appContainer.pushRepository,
                )
            }
        },
    )
    val state = viewModel.uiState
    val context = LocalContext.current

    var appLocaleTag by remember { mutableStateOf(context.currentAppLocaleTag()) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Permission result doesn't change what we do -- the service starts either way, see NtfyListenerService doc comment. */ }

    fun setPushEnabled(enabled: Boolean) {
        viewModel.setPushEnabled(enabled)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NtfyListenerService.start(context)
        } else {
            NtfyListenerService.stop(context)
        }
    }

    if (showLanguageDialog) {
        AppLanguageDialog(
            current = appLocaleTag,
            onDismiss = { showLanguageDialog = false },
            onSelect = { tag ->
                if (Build.VERSION.SDK_INT >= 33) {
                    // Writing through AppCompatDelegate here was unreliable (silently failed
                    // to persist on a non-AppCompatActivity host); the platform LocaleManager
                    // takes the write directly and reliably, and the OS auto-recreates all of
                    // the app's activities on its own -- no manual recreate() needed or wanted.
                    context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                        if (tag.isEmpty()) android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(tag)
                } else {
                    AppCompatDelegate.setApplicationLocales(
                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                    )
                    // AppCompat's automatic recreate-on-locale-change hooks into
                    // AppCompatActivity; MainActivity is a plain ComponentActivity, so on
                    // pre-33 (no platform LocaleManager) nothing else will trigger a redraw.
                    context.findActivity()?.recreate()
                }
                appLocaleTag = tag
                showLanguageDialog = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenProfile)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarImage(url = state.avatar, contentDescription = state.displayName, size = 56.dp)
                    Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(state.displayName.ifBlank { state.acct }, style = MaterialTheme.typography.titleMedium)
                        Text("@${state.acct}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionLabel(stringResource(R.string.settings_privacy_section))
                SettingsSwitchRow(
                    label = stringResource(R.string.edit_profile_locked),
                    checked = state.locked,
                    onCheckedChange = viewModel::onLockedChange,
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.edit_profile_discoverable),
                    checked = state.discoverable,
                    onCheckedChange = viewModel::onDiscoverableChange,
                )
                SettingsSwitchRow(
                    label = stringResource(R.string.edit_profile_bot),
                    checked = state.bot,
                    onCheckedChange = viewModel::onBotChange,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionLabel(stringResource(R.string.settings_app_language_section))
                SettingsLinkRow(
                    icon = Icons.Filled.Language,
                    label = appLanguageLabel(appLocaleTag),
                    hint = stringResource(R.string.settings_app_language_hint),
                    onClick = { showLanguageDialog = true },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SectionLabel(stringResource(R.string.settings_push_section))
                if (state.pushAvailable) {
                    SettingsSwitchRow(
                        label = stringResource(R.string.settings_push_enable),
                        checked = state.pushEnabled,
                        onCheckedChange = ::setPushEnabled,
                    )
                    Text(
                        stringResource(R.string.settings_push_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.settings_push_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsLinkRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = stringResource(R.string.settings_open_web),
                    hint = stringResource(R.string.settings_open_web_hint),
                    onClick = {
                        val url = viewModel.webProfileSettingsUrl()
                        if (url != null) {
                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                        }
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsLinkRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = stringResource(R.string.profile_logout),
                    hint = null,
                    onClick = { NtfyListenerService.stop(context); viewModel.logout(); onLoggedOut() },
                )

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

/**
 * AppCompatDelegate.getApplicationLocales() only reflects reality for AppCompatActivity
 * hosts -- MainActivity is a plain ComponentActivity, so its internal cache never syncs
 * even though setApplicationLocales() correctly persists to the OS (confirmed live via
 * `adb shell cmd locale get-app-locales`, and the rest of the UI genuinely re-localizes).
 * Read straight from the platform LocaleManager instead, which is authoritative on 33+;
 * AppCompatDelegate's own SharedPreferences-backed store is the real source pre-33, where
 * this divergence doesn't exist.
 */
private fun Context.currentAppLocaleTag(): String {
    if (Build.VERSION.SDK_INT >= 33) {
        return getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
    }
    return AppCompatDelegate.getApplicationLocales().toLanguageTags()
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
private fun appLanguageLabel(tag: String): String = when (tag) {
    "" -> stringResource(R.string.settings_app_language_system)
    "pl" -> "Polski"
    "en" -> "English"
    else -> tag
}

private val appLanguageOptions = listOf("" to null, "pl" to "Polski", "en" to "English")

@Composable
private fun AppLanguageDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_app_language_section)) },
        text = {
            Column(Modifier.selectableGroup()) {
                appLanguageOptions.forEach { (tag, name) ->
                    val label = name ?: stringResource(R.string.settings_app_language_system)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = tag == current, onClick = { onSelect(tag) }, role = Role.RadioButton)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tag == current, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


@Composable
private fun SettingsLinkRow(
    icon: ImageVector,
    label: String,
    hint: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(label)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class SettingsUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val acct: String = "",
    val avatar: String = "",
    val locked: Boolean = false,
    val discoverable: Boolean = true,
    val bot: Boolean = false,
    val pushAvailable: Boolean = false,
    val pushEnabled: Boolean = false,
    val error: String? = null,
)

private class SettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
    private val pushRepository: PushRepository,
) : ViewModel() {

    var uiState by mutableStateOf(SettingsUiState(pushEnabled = tokenStore.pushEnabled))
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            profileRepository.me().fold(
                onSuccess = { account ->
                    uiState = uiState.copy(
                        isLoading = false,
                        displayName = account.displayName,
                        acct = account.acct,
                        avatar = account.avatar,
                        locked = account.locked,
                        discoverable = account.discoverable,
                        bot = account.bot,
                    )
                },
                onFailure = { e -> uiState = uiState.copy(isLoading = false, error = e.message) },
            )
            pushRepository.config().onSuccess { config ->
                uiState = uiState.copy(pushAvailable = config.enabled)
            }
        }
    }

    fun setPushEnabled(value: Boolean) {
        tokenStore.pushEnabled = value
        uiState = uiState.copy(pushEnabled = value)
    }

    fun webProfileSettingsUrl(): String? = tokenStore.instanceBaseUrl?.trimEnd('/')?.plus("/settings/profile")

    fun onLockedChange(value: Boolean) = persist(uiState.copy(locked = value))

    fun onDiscoverableChange(value: Boolean) = persist(uiState.copy(discoverable = value))

    fun onBotChange(value: Boolean) = persist(uiState.copy(bot = value))

    private fun persist(newState: SettingsUiState) {
        uiState = newState
        viewModelScope.launch {
            profileRepository.updateProfile(
                displayName = null,
                note = null,
                locked = newState.locked,
                discoverable = newState.discoverable,
                bot = newState.bot,
            ).onFailure { e -> uiState = uiState.copy(error = e.message) }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}
