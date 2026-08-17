package pl.larpnet.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import pl.larpnet.android.ui.nav.LarpnetNavGraph
import pl.larpnet.android.ui.theme.LarpnetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appContainer = (application as App).appContainer
        val startDestination = if (appContainer.tokenStore.isLoggedIn) "home" else "login"

        setContent {
            LarpnetTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LarpnetNavGraph(startDestination = startDestination)
                }
            }
        }
    }
}
