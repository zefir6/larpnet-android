package pl.larpnet.android.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.DirectMessage
import pl.larpnet.android.data.repository.ConversationRepository
import pl.larpnet.android.data.repository.ProfileRepository

data class ConversationThreadUiState(
    val account: Account? = null,
    val myUsername: String? = null,
    val messages: List<DirectMessage> = emptyList(),
    val isLoading: Boolean = false,
    val draft: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
)

/**
 * [accountId] identifies the other party (already-known from an existing conversation, or
 * just picked via the new-message recipient search) -- this screen fetches the [Account] for
 * it to get [Account.url], which is what actually addresses messages server-side (see
 * ConversationRepository doc). [conversationId], when present, lets an existing conversation
 * be marked read on open.
 */
class ConversationThreadViewModel(
    private val accountId: String,
    private val conversationId: String?,
    private val conversationRepository: ConversationRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ConversationThreadUiState())
        private set

    init {
        load()
    }

    private fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val accountResult = profileRepository.account(accountId)
            val meResult = profileRepository.me()
            val account = accountResult.getOrNull()
            if (account == null) {
                uiState = uiState.copy(isLoading = false, error = accountResult.exceptionOrNull()?.message)
                return@launch
            }
            uiState = uiState.copy(account = account, myUsername = meResult.getOrNull()?.username)

            conversationRepository.thread(profileUrl = account.url).fold(
                onSuccess = { page ->
                    uiState = uiState.copy(messages = page.items.reversed(), isLoading = false)
                },
                onFailure = { e -> uiState = uiState.copy(isLoading = false, error = e.message) },
            )

            if (conversationId != null) {
                conversationRepository.markRead(conversationId)
            }
        }
    }

    fun onDraftChange(value: String) {
        uiState = uiState.copy(draft = value)
    }

    fun send() {
        val account = uiState.account ?: return
        val text = uiState.draft.trim()
        if (text.isEmpty() || uiState.isSending) return
        uiState = uiState.copy(isSending = true, error = null)
        viewModelScope.launch {
            conversationRepository.send(account.acct, text).fold(
                onSuccess = { message ->
                    uiState = uiState.copy(
                        messages = uiState.messages + message,
                        draft = "",
                        isSending = false,
                    )
                },
                onFailure = { e -> uiState = uiState.copy(isSending = false, error = e.message) },
            )
        }
    }
}
