package pl.larpnet.android.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import pl.larpnet.android.App
import pl.larpnet.android.MainActivity

/**
 * Purely a catcher for the `pl.larpnet.android://oauth` redirect (see AndroidManifest.xml's
 * intent-filter and data/auth/OAuthFlow.kt). Forwards the callback Uri to AppContainer's
 * event flow, where LoginScreen/LoginViewModel picks it up, then hands control straight back
 * to MainActivity -- this Activity never renders anything of its own.
 */
class OAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.data?.let { uri ->
            (application as App).appContainer.emitOAuthCallback(uri)
        }

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                // CLEAR_TOP is the important one here: the Custom Tab (Chrome) activity was
                // launched into MainActivity's own task (CustomTabsIntent's default), so
                // without clearing above the existing MainActivity instance, it's left sitting
                // in the back stack -- a system Back press from a top-level screen would
                // resurface the spent browser page instead of behaving like a normal top-level
                // screen. Verified live: without this flag, Back from Home -> reveals Chrome
                // -> Back again exits the app entirely.
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
        finish()
    }
}
