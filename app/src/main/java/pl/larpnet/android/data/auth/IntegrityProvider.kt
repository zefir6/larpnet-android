package pl.larpnet.android.data.auth

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play Integrity "classic" request: the server's single-use registration challenge goes in as
 * the nonce, and the server has Google decrypt the token and checks the verdicts (genuine app
 * signed by Larpnet, genuine device) -- see friendica-larpnet's
 * src/Security/LarpnetAttestation/PlayIntegrity.php.
 *
 * Needs Google Play services on the device. Without them (or when Google says no) this
 * throws, and the registration screen offers the website instead.
 */
class IntegrityProvider(private val context: Context) {

    suspend fun token(nonce: String): String = suspendCancellableCoroutine { cont ->
        IntegrityManagerFactory.create(context.applicationContext)
            .requestIntegrityToken(IntegrityTokenRequest.builder().setNonce(nonce).build())
            .addOnSuccessListener { response -> cont.resume(response.token()) }
            .addOnFailureListener { error -> cont.resumeWithException(error) }
    }
}
