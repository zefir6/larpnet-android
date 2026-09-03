package pl.larpnet.android.data.model

import kotlinx.serialization.Serializable

/** Response of `POST /larpnet_fcm` -- either {"registered": true} or {"unregistered": true}. */
@Serializable
data class FcmRegistrationResult(
    val registered: Boolean = false,
    val unregistered: Boolean = false,
)
