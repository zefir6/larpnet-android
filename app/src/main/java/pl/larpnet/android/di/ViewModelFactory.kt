package pl.larpnet.android.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import pl.larpnet.android.App

/**
 * Retrieves the singleton [AppContainer] built in App.onCreate(). Screens use this together
 * with androidx.lifecycle.viewmodel.viewModelFactory { initializer { ... } } to construct
 * their ViewModels with manually-wired repository dependencies -- see ui/timeline/TimelineScreen.kt
 * for the pattern every other screen follows.
 */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current.applicationContext
    return (context as App).appContainer
}
