package pl.larpnet.android.ui.moderation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.data.repository.ProfileRepository
import pl.larpnet.android.ui.common.ReportSheet
import pl.larpnet.android.ui.timeline.StatusModerationActions

data class ReportTarget(val accountId: String, val accountAcct: String, val statusIds: List<String>)

/**
 * Per-screen state holder (not a ViewModel -- it only owns transient dialog/sheet state, nothing
 * that needs to survive a config change) for the block/report affordances on [StatusCard]'s
 * overflow menu. One instance is created per screen root (Timeline/Thread/Profile) via
 * [rememberPostModerationHost] and its dialogs rendered once via [PostModerationDialogs], mirroring
 * how [pl.larpnet.android.ui.common.MediaGalleryDialog] is already rendered once per StatusCard
 * rather than duplicated per menu item.
 *
 * Doesn't gate on "is this my own post" -- Timeline/Thread have no cheap way to know the current
 * user's account id at this layer, so the overflow menu appears on every post there (only
 * ProfileScreen's own-profile branch swaps it for the existing onDelete-only path). Blocking or
 * reporting yourself is a harmless edge case the server rejects, not a crash.
 */
class PostModerationHost(
    private val profileRepository: ProfileRepository,
    private val hiddenPostsStore: LocalPostFilterStore,
    private val blockedPostsStore: LocalPostFilterStore,
    private val scope: CoroutineScope,
    private val onAccountBlocked: (accountId: String) -> Unit,
) {
    var reportTarget by mutableStateOf<ReportTarget?>(null)
        private set
    var blockPostTarget by mutableStateOf<Status?>(null)
        private set
    var blockAccountTarget by mutableStateOf<Account?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun actionsFor(status: Status): StatusModerationActions {
        val display = status.reblog ?: status
        return StatusModerationActions(
            onHidePost = { hiddenPostsStore.add(display.id) },
            onBlockPost = { blockPostTarget = display },
            onBlockAccount = { blockAccountTarget = display.account },
            onReportPost = { reportTarget = ReportTarget(display.account.id, display.account.acct, listOf(display.id)) },
            onReportAccount = { reportTarget = ReportTarget(display.account.id, display.account.acct, emptyList()) },
        )
    }

    fun dismissBlockPost() {
        blockPostTarget = null
    }

    fun confirmBlockPost() {
        blockPostTarget?.let { blockedPostsStore.add(it.id) }
        blockPostTarget = null
    }

    fun dismissBlockAccount() {
        blockAccountTarget = null
    }

    fun confirmBlockAccount() {
        val account = blockAccountTarget ?: return
        blockAccountTarget = null
        scope.launch {
            profileRepository.block(account.id)
                .onSuccess { onAccountBlocked(account.id) }
                .onFailure { error = it.message }
        }
    }

    fun dismissReport() {
        reportTarget = null
    }

    fun submitReport(category: String, comment: String) {
        val target = reportTarget ?: return
        reportTarget = null
        scope.launch {
            profileRepository.report(target.accountId, comment, category, target.statusIds)
                .onFailure { error = it.message }
        }
    }

    fun dismissError() {
        error = null
    }
}

@Composable
fun rememberPostModerationHost(
    profileRepository: ProfileRepository,
    hiddenPostsStore: LocalPostFilterStore,
    blockedPostsStore: LocalPostFilterStore,
    onAccountBlocked: (accountId: String) -> Unit,
): PostModerationHost {
    val scope = rememberCoroutineScope()
    return remember {
        PostModerationHost(profileRepository, hiddenPostsStore, blockedPostsStore, scope, onAccountBlocked)
    }
}

/** Renders every dialog/sheet [host]'s state can trigger. Attach once per screen root, alongside
 * the [StatusCard][pl.larpnet.android.ui.timeline.StatusCard] calls that feed it via [PostModerationHost.actionsFor]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostModerationDialogs(host: PostModerationHost) {
    host.blockPostTarget?.let {
        AlertDialog(
            onDismissRequest = host::dismissBlockPost,
            title = { Text(stringResource(R.string.moderation_block_post)) },
            text = { Text(stringResource(R.string.moderation_block_post_confirm)) },
            confirmButton = { TextButton(onClick = host::confirmBlockPost) { Text(stringResource(R.string.moderation_block_post)) } },
            dismissButton = { TextButton(onClick = host::dismissBlockPost) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }

    host.blockAccountTarget?.let { account ->
        AlertDialog(
            onDismissRequest = host::dismissBlockAccount,
            title = { Text(stringResource(R.string.moderation_block_account, account.acct)) },
            text = { Text(stringResource(R.string.moderation_block_account_confirm, account.acct)) },
            confirmButton = { TextButton(onClick = host::confirmBlockAccount) { Text(stringResource(R.string.moderation_block_account_confirm_button)) } },
            dismissButton = { TextButton(onClick = host::dismissBlockAccount) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }

    host.reportTarget?.let { target ->
        ReportSheet(
            accountAcct = target.accountAcct,
            onDismiss = host::dismissReport,
            onSubmit = { category, comment -> host.submitReport(category, comment) },
        )
    }

    host.error?.let { message ->
        AlertDialog(
            onDismissRequest = host::dismissError,
            title = { Text(stringResource(R.string.error_generic)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = host::dismissError) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}
