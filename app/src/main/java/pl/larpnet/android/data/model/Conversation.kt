package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    override val id: String,
    val accounts: List<Account> = emptyList(),
    val unread: Boolean = false,
    @SerialName("last_status") val lastStatus: Status? = null,
) : Identifiable
