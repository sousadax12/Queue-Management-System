package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.operator.R
import pro.joaosousa.queuedisplay.operator.session.LoginFailure
import pro.joaosousa.queuedisplay.operator.session.LoginForm

/**
 * Email/password login for a remote (design F3). Enter on the email field moves to the password,
 * Enter on the password submits; the on-screen keyboard's Next/Done do the same. The email is
 * trimmed. A failed sign-in shows "Login failed" with the mapped message over the form.
 */
@Composable
fun LoginScreen(
    signingIn: Boolean,
    failure: LoginFailure?,
    onSubmit: (email: String, password: String) -> Unit,
    onDismissFailure: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf(LoginForm.Errors()) }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    fun submit() {
        if (signingIn) return
        val checked = LoginForm.validate(email, password)
        errors = checked
        when {
            checked.isEmpty -> onSubmit(email.trim(), password)
            checked.email != null -> emailFocus.requestFocus()
            else -> passwordFocus.requestFocus()
        }
    }

    Centered {
        Title(stringResource(R.string.login_title))
        Spacer(Modifier.height(24.dp))
        LoginField(
            label = stringResource(R.string.login_email),
            value = email,
            onValueChange = { email = it },
            error = errors.email?.let { stringResource(R.string.login_missing_email) },
            readOnly = signingIn,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            onEnter = { passwordFocus.requestFocus() },
            modifier = Modifier
                .focusRequester(emailFocus)
                .semantics { contentType = ContentType.EmailAddress + ContentType.Username }
                .testTag(TestTags.EMAIL),
        )
        Spacer(Modifier.height(16.dp))
        LoginField(
            label = stringResource(R.string.login_password),
            value = password,
            onValueChange = { password = it },
            error = errors.password?.let { stringResource(R.string.login_missing_password) },
            readOnly = signingIn,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            onEnter = { submit() },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .focusRequester(passwordFocus)
                .semantics { contentType = ContentType.Password }
                .testTag(TestTags.PASSWORD),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { submit() }, modifier = Modifier.testTag(TestTags.SUBMIT)) {
            Text(stringResource(if (signingIn) R.string.login_signing_in else R.string.login_submit))
        }
    }

    // Autofocus the email, as Flutter does; after an error, go back to the field to fix.
    LaunchedEffect(failure, signingIn) {
        if (!signingIn && failure == null) {
            if (email.isBlank()) emailFocus.requestFocus() else passwordFocus.requestFocus()
        }
    }

    if (failure != null) {
        LoginFailedDialog(failure = failure, onDismiss = onDismissFailure)
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    readOnly: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            readOnly = readOnly,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .onEnterKey { if (!readOnly) onEnter() }
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = when {
                        error != null -> colors.error
                        focused -> colors.primary
                        else -> colors.onSurface.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = error, color = colors.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Hardware Enter (a remote's OK on many boxes, or a keyboard): acts on key up and swallows both
 * halves, so the key up never lands on the field that the key down moved focus to.
 */
private fun Modifier.onEnterKey(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyUp) action()
    true
}

@Composable
private fun LoginFailedDialog(failure: LoginFailure, onDismiss: () -> Unit) {
    val message = when (failure) {
        LoginFailure.Known.WrongCredentials -> stringResource(R.string.login_wrong_credentials)
        LoginFailure.Known.InvalidEmail -> stringResource(R.string.login_invalid_email)
        LoginFailure.Known.UserDisabled -> stringResource(R.string.login_user_disabled)
        LoginFailure.Known.TooManyRequests -> stringResource(R.string.login_too_many_requests)
        LoginFailure.Known.NoNetwork -> stringResource(R.string.login_no_network)
        is LoginFailure.Other -> failure.text
    }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(32.dp)) {
                Title(stringResource(R.string.login_failed))
                Spacer(Modifier.height(16.dp))
                Text(message)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.focusRequester(focus).testTag(TestTags.MESSAGE_ACTION)) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
    LaunchedEffect(failure) { focus.requestFocus() }
}
