package pl.larpnet.android.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.matrix.MatrixRepository

enum class RecoveryKeyMode { SETUP, RESET, RESTORE, PRIVATE }

data class RecoveryKeyUiState(
    val recoveryKey: String? = null,
    val isBusy: Boolean = false,
    val restoreSucceeded: Boolean = false,
    val passphraseInput: String = "",
    val restoreInput: String = "",
    val error: String? = null,
)

/**
 * Direct port of the web client's `recovery.js`/`RecoveryKeyModal.jsx`:
 * - [RecoveryKeyMode.SETUP]/[RecoveryKeyMode.RESET]: choose a random key or a custom
 *   passphrase, then show the resulting encoded key once (there's no way to see it again -- we
 *   never keep a copy).
 * - [RecoveryKeyMode.RESTORE]: this device doesn't have local access to already-existing
 *   recovery yet -- enter the saved key *or* the phrase it was set up with (see
 *   [MatrixRepository.restoreRecovery]'s doc comment for why the same field accepts both).
 *   [legacy]: the account is being moved to standard encryption mode but still has the user's
 *   own old key -- after it unlocks this device, [MatrixRepository.ensureEncryption] migrates.
 * - [RecoveryKeyMode.PRIVATE]: same choose-then-show flow as RESET, for switching from standard
 *   to private encryption mode ([MatrixRepository.switchToPrivate]).
 */
class RecoveryKeyViewModel(
    val mode: RecoveryKeyMode,
    private val repository: MatrixRepository,
    private val legacy: Boolean = false,
) : ViewModel() {

    var uiState by mutableStateOf(RecoveryKeyUiState())
        private set

    fun onPassphraseInputChange(value: String) {
        uiState = uiState.copy(passphraseInput = value)
    }

    fun onRestoreInputChange(value: String) {
        uiState = uiState.copy(restoreInput = value)
    }

    fun chooseRandom() = choose(passphrase = null)

    fun choosePassphrase() {
        val trimmed = uiState.passphraseInput.trim()
        if (trimmed.isEmpty()) return
        choose(passphrase = trimmed)
    }

    fun submitRestore() {
        val trimmed = uiState.restoreInput.trim()
        if (trimmed.isEmpty()) return
        uiState = uiState.copy(isBusy = true)
        viewModelScope.launch {
            try {
                repository.restoreRecovery(trimmed)
                if (legacy) {
                    // Now unlocked with the old key -- migrate to the server passphrase. A
                    // failure here just means the next session retries; history is already readable.
                    runCatching { repository.ensureEncryption() }
                }
                uiState = uiState.copy(isBusy = false, restoreSucceeded = true, error = null)
            } catch (e: Exception) {
                uiState = uiState.copy(isBusy = false, error = "Nieprawidłowy klucz lub fraza.")
            }
        }
    }

    private fun choose(passphrase: String?) {
        uiState = uiState.copy(isBusy = true)
        viewModelScope.launch {
            try {
                val key = when (mode) {
                    RecoveryKeyMode.SETUP -> repository.setUpRecovery(passphrase)
                    RecoveryKeyMode.RESET -> repository.resetRecovery(passphrase)
                    RecoveryKeyMode.PRIVATE -> repository.switchToPrivate(passphrase)
                    RecoveryKeyMode.RESTORE -> return@launch
                }
                uiState = uiState.copy(isBusy = false, recoveryKey = key, error = null)
            } catch (e: MatrixRepository.DeviceLockedException) {
                uiState = uiState.copy(isBusy = false, error = "Najpierw odblokuj historię czatu na tym urządzeniu.")
            } catch (e: Exception) {
                uiState = uiState.copy(isBusy = false, error = e.message)
            }
        }
    }
}
