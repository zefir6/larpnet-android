package pl.larpnet.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Instance(
    val uri: String = "",
    val title: String = "",
    val description: String = "",
    val version: String = "",
)
