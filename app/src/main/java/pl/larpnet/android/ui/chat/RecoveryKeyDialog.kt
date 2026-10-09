package pl.larpnet.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.data.matrix.MatrixRepository

/**
 * Full-screen [Dialog] -- setup/restore from `ChatScreen` right after login (see
 * `MatrixRepository.recoveryPromptKind()`), reset from `SettingsScreen`. Non-dismissable for
 * [RecoveryKeyMode.SETUP]/[RecoveryKeyMode.RESET] until a key is chosen and confirmed (there's
 * nothing sensible to skip to); [RecoveryKeyMode.RESTORE] allows "Później" since new messages
 * still work without it, and [RecoveryKeyMode.PRIVATE] can be cancelled until a key is chosen.
 */
@Composable
fun RecoveryKeyDialog(
    mode: RecoveryKeyMode,
    repository: MatrixRepository,
    onDone: () -> Unit,
    onSkip: (() -> Unit)? = null,
    instanceKey: Any = Unit,
    legacy: Boolean = false,
) {
    val viewModel: RecoveryKeyViewModel = viewModel(
        key = "recovery_key_${mode}_${legacy}_$instanceKey",
        factory = viewModelFactory {
            initializer { RecoveryKeyViewModel(mode, repository, legacy) }
        },
    )
    val state = viewModel.uiState
    val dismissable = (mode == RecoveryKeyMode.RESTORE && !state.restoreSucceeded) ||
        (mode == RecoveryKeyMode.PRIVATE && state.recoveryKey == null && !state.isBusy)

    LaunchedEffect(state.restoreSucceeded) {
        if (state.restoreSucceeded) onDone()
    }

    Dialog(
        onDismissRequest = { if (dismissable) onSkip?.invoke() },
        properties = DialogProperties(dismissOnClickOutside = dismissable, dismissOnBackPress = dismissable),
    ) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when (mode) {
                        RecoveryKeyMode.SETUP -> stringResource(R.string.recovery_title_setup)
                        RecoveryKeyMode.RESET -> stringResource(R.string.recovery_title_reset)
                        RecoveryKeyMode.RESTORE -> stringResource(R.string.recovery_title_restore)
                        RecoveryKeyMode.PRIVATE -> stringResource(R.string.encryption_switch_to_private)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                when {
                    mode == RecoveryKeyMode.RESTORE -> RestoreBody(state, viewModel, onSkip, legacy)
                    state.recoveryKey != null -> ShowKeyBody(state.recoveryKey, onDone)
                    else -> ChooseBody(mode, state, viewModel, onSkip)
                }
            }
        }
    }
}

@Composable
private fun ChooseBody(mode: RecoveryKeyMode, state: RecoveryKeyUiState, viewModel: RecoveryKeyViewModel, onSkip: (() -> Unit)?) {
    Text(
        text = when (mode) {
            RecoveryKeyMode.RESET -> stringResource(R.string.recovery_reset_explanation)
            RecoveryKeyMode.PRIVATE -> stringResource(R.string.encryption_private_explanation)
            else -> stringResource(R.string.recovery_setup_explanation)
        },
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = viewModel::chooseRandom, enabled = !state.isBusy) {
        Text(stringResource(R.string.recovery_generate_random))
    }
    OutlinedTextField(
        value = state.passphraseInput,
        onValueChange = viewModel::onPassphraseInputChange,
        placeholder = { Text(stringResource(R.string.recovery_own_phrase_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(
        onClick = viewModel::choosePassphrase,
        enabled = !state.isBusy && state.passphraseInput.isNotBlank(),
    ) {
        Text(stringResource(R.string.recovery_set_phrase))
    }
    if (state.isBusy) CircularProgressIndicator()
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (mode == RecoveryKeyMode.PRIVATE && !state.isBusy) {
        onSkip?.let { skip -> TextButton(onClick = skip) { Text(stringResource(R.string.dialog_cancel)) } }
    }
}

@Composable
private fun ShowKeyBody(key: String, onDone: () -> Unit) {
    Text(
        text = stringResource(R.string.recovery_save_explanation),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(key, fontFamily = FontFamily.Monospace)
    TextButton(onClick = onDone) { Text(stringResource(R.string.recovery_key_saved)) }
}

@Composable
private fun RestoreBody(state: RecoveryKeyUiState, viewModel: RecoveryKeyViewModel, onSkip: (() -> Unit)?, legacy: Boolean) {
    Text(
        text = stringResource(if (legacy) R.string.recovery_restore_legacy_explanation else R.string.recovery_restore_explanation),
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = state.restoreInput,
        onValueChange = viewModel::onRestoreInputChange,
        placeholder = { Text(stringResource(R.string.recovery_key_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.isBusy) {
        CircularProgressIndicator()
    } else {
        TextButton(onClick = viewModel::submitRestore, enabled = state.restoreInput.isNotBlank()) {
            Text(stringResource(R.string.recovery_unlock))
        }
        onSkip?.let { skip -> TextButton(onClick = skip) { Text(stringResource(R.string.recovery_later)) } }
    }
}

/** Standard mode's "show my recovery phrase" -- the server-held passphrase, for use in another
 * Matrix client (e.g. Element's "Security Phrase"). */
@Composable
fun RecoveryPhraseDialog(phrase: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.encryption_show_phrase), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.encryption_show_phrase_explanation), style = MaterialTheme.typography.bodySmall)
                SelectionContainer { Text(phrase, fontFamily = FontFamily.Monospace) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.encryption_close)) }
            }
        }
    }
}
