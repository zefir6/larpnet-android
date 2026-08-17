package pl.larpnet.android.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.di.rememberAppContainer

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val appContainer = rememberAppContainer()
    val context = LocalContext.current
    val viewModel: LoginViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LoginViewModel(appContainer.authRepository) }
        },
    )

    LaunchedEffect(Unit) {
        appContainer.oauthCallbackEvents.collect { uri -> viewModel.handleCallback(uri) }
    }

    LaunchedEffect(viewModel.uiState) {
        if (viewModel.uiState is LoginUiState.LoggedIn) onLoggedIn()
    }

    // See LoginViewModel.onScreenResumed doc: recovers from the user backing out of the
    // Custom Tab, which otherwise leaves the screen stuck on "Opening browser...".
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        OutlinedTextField(
            value = viewModel.instanceUrl,
            onValueChange = viewModel::onInstanceUrlChange,
            label = { Text(stringResource(R.string.login_instance_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val state = viewModel.uiState
        if (state is LoginUiState.Error) {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = { viewModel.startLogin(context) },
            enabled = state !is LoginUiState.AwaitingBrowser && state !is LoginUiState.ExchangingToken,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.login_button))
        }

        if (state is LoginUiState.AwaitingBrowser || state is LoginUiState.ExchangingToken) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.login_opening_browser),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
