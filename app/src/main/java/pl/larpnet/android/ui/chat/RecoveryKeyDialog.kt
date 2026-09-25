package pl.larpnet.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.data.matrix.MatrixRepository

/**
 * Full-screen [Dialog] -- setup/restore from `ChatScreen` right after login (see
 * `MatrixRepository.recoveryPromptKind()`), reset from `SettingsScreen`. Non-dismissable for
 * [RecoveryKeyMode.SETUP]/[RecoveryKeyMode.RESET] until a key is chosen and confirmed (there's
 * nothing sensible to skip to); [RecoveryKeyMode.RESTORE] allows "Później" since new messages
 * still work without it.
 */
@Composable
fun RecoveryKeyDialog(
    mode: RecoveryKeyMode,
    repository: MatrixRepository,
    onDone: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val viewModel: RecoveryKeyViewModel = viewModel(
        key = "recovery_key_$mode",
        factory = viewModelFactory {
            initializer { RecoveryKeyViewModel(mode, repository) }
        },
    )
    val state = viewModel.uiState
    val dismissable = mode == RecoveryKeyMode.RESTORE && !state.restoreSucceeded

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
                        RecoveryKeyMode.SETUP -> "Ustaw klucz odzyskiwania"
                        RecoveryKeyMode.RESET -> "Resetuj klucz odzyskiwania"
                        RecoveryKeyMode.RESTORE -> "Odblokuj historię czatu"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                when {
                    mode == RecoveryKeyMode.RESTORE -> RestoreBody(state, viewModel, onSkip)
                    state.recoveryKey != null -> ShowKeyBody(state.recoveryKey, onDone)
                    else -> ChooseBody(mode, state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ChooseBody(mode: RecoveryKeyMode, state: RecoveryKeyUiState, viewModel: RecoveryKeyViewModel) {
    Text(
        text = if (mode == RecoveryKeyMode.RESET) {
            "Stary klucz przestanie działać. Wybierz nowy -- losowy albo własną frazę."
        } else {
            "Ten klucz pozwala odczytać historię czatu na nowym urządzeniu. Możesz wygenerować " +
                "losowy klucz albo ustawić własną, łatwą do zapamiętania frazę."
        },
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = viewModel::chooseRandom, enabled = !state.isBusy) {
        Text("Wygeneruj losowy klucz")
    }
    OutlinedTextField(
        value = state.passphraseInput,
        onValueChange = viewModel::onPassphraseInputChange,
        placeholder = { Text("Albo wpisz własną frazę…") },
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(
        onClick = viewModel::choosePassphrase,
        enabled = !state.isBusy && state.passphraseInput.isNotBlank(),
    ) {
        Text("Ustaw frazę")
    }
    if (state.isBusy) CircularProgressIndicator()
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun ShowKeyBody(key: String, onDone: () -> Unit) {
    Text(
        text = "Zapisz go w bezpiecznym miejscu (np. menedżerze haseł) -- nikt inny, w tym " +
            "administrator serwera, go nie zna i nie może go odzyskać. Jeśli ustawiłeś/-aś " +
            "własną frazę, możesz użyć jej zamiast tego klucza na innym urządzeniu.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(key, fontFamily = FontFamily.Monospace)
    TextButton(onClick = onDone) { Text("Zapisałem/-am klucz") }
}

@Composable
private fun RestoreBody(state: RecoveryKeyUiState, viewModel: RecoveryKeyViewModel, onSkip: (() -> Unit)?) {
    Text(
        text = "To nowe urządzenie -- wpisz swój klucz odzyskiwania (albo frazę, jeśli taką " +
            "ustawiłeś/-aś), aby odczytać wcześniejsze wiadomości. Możesz to zrobić później -- " +
            "nowe wiadomości będą działać już teraz.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = state.restoreInput,
        onValueChange = viewModel::onRestoreInputChange,
        placeholder = { Text("Klucz odzyskiwania lub fraza…") },
        modifier = Modifier.fillMaxWidth(),
    )
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.isBusy) {
        CircularProgressIndicator()
    } else {
        TextButton(onClick = viewModel::submitRestore, enabled = state.restoreInput.isNotBlank()) {
            Text("Odblokuj")
        }
        onSkip?.let { skip -> TextButton(onClick = skip) { Text("Później") } }
    }
}
