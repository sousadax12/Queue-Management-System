package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.operator.R
import pro.joaosousa.queuedisplay.operator.session.LoginFailure
import pro.joaosousa.queuedisplay.operator.session.LoginForm
import pro.joaosousa.queuedisplay.operator.session.NoAccessReason

/**
 * Email/password login for a remote (design F3), laid out as Flutter's (`lib/pages/login_page.dart`):
 * an app bar, the two underlined fields across the screen and the Login button under them. Enter
 * on the email field moves to the password, Enter on the password submits; the on-screen
 * keyboard's Next/Done do the same. The email is trimmed. While signing in, a spinner replaces the
 * form, as in Flutter; unlike Flutter, what was typed is still there after a failure. A failed
 * sign-in shows "Login failed" with the mapped message, and an account without access shows
 * "No operator access" ([noAccess]), both as Flutter's alert dialog over the form.
 */
@Composable
fun LoginScreen(
    signingIn: Boolean,
    failure: LoginFailure?,
    onSubmit: (email: String, password: String) -> Unit,
    onDismissFailure: () -> Unit,
    noAccess: NoAccessReason? = null,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf(LoginForm.Errors()) }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val dialogShown = failure != null || noAccess != null

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

    FlutterScaffold(title = stringResource(R.string.login_title)) {
        if (signingIn) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) { Spinner() }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                LoginField(
                    label = stringResource(R.string.login_email),
                    value = email,
                    onValueChange = { email = it },
                    error = errors.email?.let { stringResource(R.string.login_missing_email) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                    onEnter = { passwordFocus.requestFocus() },
                    modifier = Modifier
                        .focusRequester(emailFocus)
                        .semantics { contentType = ContentType.EmailAddress + ContentType.Username }
                        .testTag(TestTags.EMAIL),
                )
                LoginField(
                    label = stringResource(R.string.login_password),
                    value = password,
                    onValueChange = { password = it },
                    error = errors.password?.let { stringResource(R.string.login_missing_password) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    onEnter = { submit() },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .focusRequester(passwordFocus)
                        .semantics { contentType = ContentType.Password }
                        .testTag(TestTags.PASSWORD),
                )
                // Flutter's 16 dp gap, then the 4 dp above its 40 dp button in a 48 dp target.
                Spacer(Modifier.height(20.dp))
                FlutterButton(
                    text = stringResource(R.string.login_submit),
                    onClick = { submit() },
                    modifier = Modifier.testTag(TestTags.SUBMIT),
                )
            }
        }
    }

    // Autofocus the email, as Flutter does; after an error, go back to the field to fix.
    LaunchedEffect(dialogShown, signingIn) {
        if (!signingIn && !dialogShown) {
            if (email.isBlank()) emailFocus.requestFocus() else passwordFocus.requestFocus()
        }
    }

    when {
        failure != null -> LoginFailedDialog(failure = failure, onDismiss = onDismissFailure)
        noAccess != null -> NoAccessDialog(reason = noAccess, onOk = onDismissFailure)
    }
}

/**
 * Flutter's default Material 3 `TextFormField`: 56 dp high, no fill, an underline (1 dp, or 2 dp
 * in the primary colour when focused), and a label that rests on the text line and floats above
 * it once the field is focused or filled. An error turns label and underline red and adds the
 * message under the field.
 */
@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    val floating = focused || value.isNotEmpty()
    val accent = when {
        error != null -> FlutterColors.Error
        focused -> FlutterColors.Primary
        else -> null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = FlutterText.BodyLarge.copy(color = FlutterColors.OnSurface),
            cursorBrush = SolidColor(if (error != null) FlutterColors.Error else FlutterColors.Primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
                .onFocusChanged { focused = it.isFocused }
                .onEnterKey(onEnter)
                .drawBehind {
                    val stroke = (if (focused) 2.dp else 1.dp).toPx()
                    drawRect(
                        color = accent ?: FlutterColors.Outline,
                        topLeft = Offset(0f, size.height - stroke),
                        size = Size(size.width, stroke),
                    )
                },
            decorationBox = { field ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (floating) {
                        Text(
                            text = label,
                            style = FlutterText.BodySmall,
                            color = accent ?: FlutterColors.OnSurfaceVariant,
                            modifier = Modifier.align(Alignment.TopStart).padding(top = 6.dp),
                        )
                        Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { field() }
                    } else {
                        Text(
                            text = label,
                            style = FlutterText.BodyLarge,
                            color = accent ?: FlutterColors.OnSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        // Still laid out, so the field keeps its text layout and cursor.
                        Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)) { field() }
                    }
                }
            },
        )
        if (error != null) {
            Text(
                text = error,
                style = FlutterText.BodySmall,
                color = FlutterColors.Error,
                modifier = Modifier.padding(top = 8.dp),
            )
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
    FlutterAlertDialog(
        title = stringResource(R.string.login_failed),
        message = message,
        action = stringResource(R.string.ok),
        onAction = onDismiss,
    )
}

/** "No operator access": the account was signed out; OK returns to the login form. */
@Composable
private fun NoAccessDialog(reason: NoAccessReason, onOk: () -> Unit) {
    FlutterAlertDialog(
        title = stringResource(R.string.no_access_title),
        message = stringResource(
            when (reason) {
                NoAccessReason.NoOperatorRole -> R.string.no_access_no_role
                NoAccessReason.EmailNotVerified -> R.string.no_access_unverified
            },
        ),
        action = stringResource(R.string.ok),
        onAction = onOk,
    )
}
