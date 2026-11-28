package pl.larpnet.android.ui.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.BuildConfig
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.data.repository.ConfirmResult
import pl.larpnet.android.data.repository.RegisterResult
import pl.larpnet.android.data.repository.RegistrationForm
import pl.larpnet.android.data.repository.RegistrationRepository

sealed interface RegisterStep {
    data object Form : RegisterStep
    data class Code(val id: String, val email: String, val approvalRequired: Boolean) : RegisterStep
    data object ApprovalPending : RegisterStep
    /** Confirmed, but log in the normal way (link clicked, or no token for this app). */
    data object LogIn : RegisterStep
    data object LoggedIn : RegisterStep
}

/** A message to show: the server's own text when it sent one, otherwise a generic one. */
data class RegisterMessage(val serverText: String?, val useWebsite: Boolean = false, val info: Boolean = false)

class RegisterViewModel(
    private val repository: RegistrationRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    /** Same instance choice as LoginViewModel: the stored one (Settings can change it), else larpnet.pl. */
    val instanceUrl: String
        get() = tokenStore.instanceBaseUrl?.trim()?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_INSTANCE

    var step by mutableStateOf<RegisterStep>(RegisterStep.Form)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<RegisterMessage?>(null)
        private set

    var displayName by mutableStateOf("")
    var username by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordRepeat by mutableStateOf("")
    var reason by mutableStateOf("")
    var agreed by mutableStateOf(false)
    var code by mutableStateOf("")

    val passwordsMatch: Boolean get() = passwordRepeat.isEmpty() || password == passwordRepeat

    val canSubmit: Boolean
        get() = !busy && displayName.isNotBlank() && username.isNotBlank() && email.contains('@') &&
            password.length >= 8 && password == passwordRepeat && agreed

    val canConfirm: Boolean get() = !busy && code.count(Char::isDigit) == 6

    fun submit() {
        if (!canSubmit) return
        busy = true
        message = null
        viewModelScope.launch {
            val form = RegistrationForm(displayName, username, email, password, reason, agreed)
            when (val result = repository.register(instanceUrl, form)) {
                is RegisterResult.Started -> {
                    password = ""
                    passwordRepeat = ""
                    step = RegisterStep.Code(result.id, result.email, result.approvalRequired)
                }
                is RegisterResult.Failed -> message = RegisterMessage(result.message, result.useWebsite)
            }
            busy = false
        }
    }

    fun confirm() {
        val current = step as? RegisterStep.Code ?: return
        if (!canConfirm) return
        busy = true
        message = null
        viewModelScope.launch {
            when (val result = repository.confirm(instanceUrl, current.id, code)) {
                ConfirmResult.LoggedIn -> step = RegisterStep.LoggedIn
                ConfirmResult.ApprovalPending -> step = RegisterStep.ApprovalPending
                ConfirmResult.ConfirmedLogIn -> step = RegisterStep.LogIn
                is ConfirmResult.Failed -> {
                    message = RegisterMessage(result.message)
                    code = ""
                    if (result.expired) step = RegisterStep.Form
                }
            }
            busy = false
        }
    }

    fun resend() {
        val current = step as? RegisterStep.Code ?: return
        busy = true
        message = null
        viewModelScope.launch {
            val error = repository.resend(instanceUrl, current.id)
            message = if (error == null) RegisterMessage(null, info = true) else RegisterMessage(error.ifBlank { null })
            busy = false
        }
    }

    /** "I clicked the link in the email": confirmed -- log in the normal way, or wait for approval. */
    fun linkUsed() {
        val current = step as? RegisterStep.Code ?: return
        step = if (current.approvalRequired) RegisterStep.ApprovalPending else RegisterStep.LogIn
    }
}
