package pl.larpnet.android.ui.register

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.network.InstanceUrl

/**
 * In-app account registration: form -> code from the email -> logged in (or "waits for
 * approval"). See RegistrationRepository for the server side.
 *
 * [onLogIn] goes back to the login screen and starts the normal browser login, for when the
 * account is confirmed but the app didn't get a token (the email link was used instead).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onBack: () -> Unit, onLoggedIn: () -> Unit, onLogIn: () -> Unit) {
    val appContainer = rememberAppContainer()
    val context = LocalContext.current
    val viewModel: RegisterViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RegisterViewModel(appContainer.registrationRepository, appContainer.tokenStore) }
        },
    )
    val openInBrowser = { path: String ->
        CustomTabsIntent.Builder().build()
            .launchUrl(context, (InstanceUrl.normalize(viewModel.instanceUrl) + path).toUri())
    }

    LaunchedEffect(viewModel.step) {
        if (viewModel.step is RegisterStep.LoggedIn) onLoggedIn()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.register_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.register_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            viewModel.message?.let { message ->
                Text(
                    text = when {
                        message.info -> stringResource(R.string.register_code_resent)
                        message.useWebsite -> message.serverText ?: stringResource(R.string.register_error_attestation)
                        else -> message.serverText ?: stringResource(R.string.register_error_generic)
                    },
                    color = if (message.info) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (message.useWebsite) {
                    OutlinedButton(onClick = { openInBrowser("register") }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.register_on_website))
                    }
                }
            }

            when (val step = viewModel.step) {
                RegisterStep.Form -> RegisterForm(viewModel, onOpenTerms = { openInBrowser("tos") }, onOpenWebsite = { openInBrowser("register") })
                is RegisterStep.Code -> CodeStep(viewModel, step)
                RegisterStep.ApprovalPending -> Finished(stringResource(R.string.register_approval_pending), onLogIn = onBack)
                RegisterStep.LogIn -> Finished(stringResource(R.string.register_confirmed_log_in), onLogIn = onLogIn)
                RegisterStep.LoggedIn -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun ColumnScope.RegisterForm(viewModel: RegisterViewModel, onOpenTerms: () -> Unit, onOpenWebsite: () -> Unit) {
    OutlinedTextField(
        value = viewModel.displayName,
        onValueChange = { viewModel.displayName = it },
        label = { Text(stringResource(R.string.register_display_name)) },
        supportingText = { Text(stringResource(R.string.register_display_name_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = viewModel.username,
        onValueChange = { value -> viewModel.username = value.lowercase().filter { it.isLetterOrDigit() || it == '_' } },
        label = { Text(stringResource(R.string.register_username)) },
        supportingText = { Text(stringResource(R.string.register_username_hint, InstanceUrl.normalize(viewModel.instanceUrl).removePrefix("https://").trimEnd('/'))) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = viewModel.email,
        onValueChange = { viewModel.email = it.trim() },
        label = { Text(stringResource(R.string.register_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = viewModel.password,
        onValueChange = { viewModel.password = it },
        label = { Text(stringResource(R.string.register_password)) },
        supportingText = { Text(stringResource(R.string.register_password_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = viewModel.passwordRepeat,
        onValueChange = { viewModel.passwordRepeat = it },
        label = { Text(stringResource(R.string.register_password_repeat)) },
        isError = !viewModel.passwordsMatch,
        supportingText = if (!viewModel.passwordsMatch) {
            { Text(stringResource(R.string.register_password_mismatch)) }
        } else {
            null
        },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = viewModel.reason,
        onValueChange = { viewModel.reason = it },
        label = { Text(stringResource(R.string.register_reason)) },
        supportingText = { Text(stringResource(R.string.register_reason_hint)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .toggleable(value = viewModel.agreed, role = Role.Checkbox, onValueChange = { viewModel.agreed = it }),
        ) {
            Checkbox(checked = viewModel.agreed, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Text(stringResource(R.string.register_agree))
        }
        TextButton(onClick = onOpenTerms) { Text(stringResource(R.string.register_terms)) }
    }
    Button(onClick = viewModel::submit, enabled = viewModel.canSubmit, modifier = Modifier.fillMaxWidth()) {
        if (viewModel.busy) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text(stringResource(R.string.register_submit))
    }
    TextButton(onClick = onOpenWebsite, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.register_on_website))
    }
}

@Composable
private fun ColumnScope.CodeStep(viewModel: RegisterViewModel, step: RegisterStep.Code) {
    Text(stringResource(R.string.register_code_sent, step.email), style = MaterialTheme.typography.bodyLarge)
    if (step.approvalRequired) {
        Text(stringResource(R.string.register_code_approval_note), style = MaterialTheme.typography.bodyMedium)
    }
    OutlinedTextField(
        value = viewModel.code,
        onValueChange = { value -> viewModel.code = value.filter(Char::isDigit).take(6) },
        label = { Text(stringResource(R.string.register_code)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = viewModel::confirm, enabled = viewModel.canConfirm, modifier = Modifier.fillMaxWidth()) {
        if (viewModel.busy) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text(stringResource(R.string.register_confirm))
    }
    TextButton(onClick = viewModel::resend, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.register_resend))
    }
    TextButton(
        onClick = viewModel::linkUsed,
        enabled = !viewModel.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.register_link_used))
    }
    Text(stringResource(R.string.register_spam_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Finished(text: String, onLogIn: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onLogIn, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_button))
    }
}
