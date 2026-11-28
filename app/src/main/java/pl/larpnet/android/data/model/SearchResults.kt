package pl.larpnet.android.data.model

import kotlinx.serialization.Serializable

/** `api/v2/search`. Hashtags are left out: only link resolution uses this. */
@Serializable
data class SearchResults(
    val accounts: List<Account> = emptyList(),
    val statuses: List<Status> = emptyList(),
)
