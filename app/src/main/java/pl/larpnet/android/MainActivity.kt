package pl.larpnet.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import pl.larpnet.android.push.PushNotifications
import pl.larpnet.android.ui.nav.LarpnetNavGraph
import pl.larpnet.android.ui.theme.LarpnetTheme

class MainActivity : ComponentActivity() {
    /** Room id from a tapped chat notification, consumed (reset to null) by the nav graph once
     * it has navigated there. singleTop: a tap while the app is open arrives via [onNewIntent]. */
    private val pendingChatRoomId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as App).appContainer
        val startDestination = if (appContainer.tokenStore.isLoggedIn) "home" else "login"
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            LarpnetTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LarpnetNavGraph(startDestination = startDestination, pendingChatRoomId = pendingChatRoomId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(PushNotifications.EXTRA_CHAT_ROOM_ID)?.let { pendingChatRoomId.value = it }
    }
}
