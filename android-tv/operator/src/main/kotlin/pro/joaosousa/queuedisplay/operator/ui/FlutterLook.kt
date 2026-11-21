package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import androidx.tv.material3.lightColorScheme

/*
 * The look of the Flutter operator app (`lib/main.dart`: `ThemeData(primarySwatch: Colors.blue)`,
 * which under Material 3 is the baseline light scheme), so operators see the screens they know.
 * Sizes and colours were measured on the Flutter app running on the TV emulator (issue #49,
 * `docs/screenshots/ui-parity/`). Only the operator uses this; the TV display keeps its dark theme.
 */

/** Flutter's Material 3 baseline light colours. */
internal object FlutterColors {
    val Primary = Color(0xFF6750A4)
    val OnPrimary = Color(0xFFFFFFFF)
    val Surface = Color(0xFFFEF7FF)
    val OnSurface = Color(0xFF1D1B20)
    val OnSurfaceVariant = Color(0xFF49454F)
    val Outline = Color(0xFF79747E)
    val SurfaceContainerLow = Color(0xFFF7F2FA)
    val SurfaceContainerHigh = Color(0xFFECE6F0)
    val Error = Color(0xFFB3261E)

    /** `Colors.greenAccent`: the highlighted setup tile. */
    val GreenAccent = Color(0xFF69F0AE)

    /** `Colors.grey`: the border of the other setup tiles. */
    val Grey = Color(0xFF9E9E9E)

    /** `Colors.black54`: the barrier behind a dialog. */
    const val SCRIM_ALPHA = 0.54f
}

/** Flutter's Material 3 styles use `TextLeadingDistribution.even`: the extra leading is split in half. */
private val EvenLeading = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Flutter's Material 3 text styles (Roboto on Android, as here). */
internal object FlutterText {
    val TitleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, lineHeightStyle = EvenLeading)
    val HeadlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, lineHeightStyle = EvenLeading)
    val BodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp, lineHeightStyle = EvenLeading)
    val BodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp, lineHeightStyle = EvenLeading)
    val BodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp, lineHeightStyle = EvenLeading)
    val LabelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        fontWeight = FontWeight.Medium,
        lineHeightStyle = EvenLeading,
    )

    /**
     * What a Flutter `Text` with its own `fontSize` still inherits from `bodyMedium`: the 0.25
     * letter spacing and the 1.43 line height. The queue tiles, clock and ticker are drawn so.
     */
    val Inherited = TextStyle(letterSpacing = 0.25.sp, lineHeight = 1.43.em, lineHeightStyle = EvenLeading)
}

private val OperatorColors = lightColorScheme(
    primary = FlutterColors.Primary,
    onPrimary = FlutterColors.OnPrimary,
    background = FlutterColors.Surface,
    onBackground = FlutterColors.OnSurface,
    surface = FlutterColors.Surface,
    onSurface = FlutterColors.OnSurface,
    onSurfaceVariant = FlutterColors.OnSurfaceVariant,
    error = FlutterColors.Error,
    border = FlutterColors.Outline,
)

/** The operator app's theme: Flutter's light Material 3 look, body text in `bodyMedium`. */
@Composable
fun OperatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OperatorColors) {
        ProvideTextStyle(FlutterText.BodyMedium, content)
    }
}

/** Flutter's `Scaffold` with an `AppBar`: a 56 dp bar with the title, then the body. */
@Composable
internal fun FlutterScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    body: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = FlutterText.TitleLarge,
                color = FlutterColors.OnSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            )
            actions()
        }
        Box(modifier = Modifier.fillMaxSize()) { body() }
    }
}

/**
 * Flutter's `ElevatedButton`: a 40 dp pill on `surfaceContainerLow` with the label in the primary
 * colour. Focused, it turns solid primary, so a remote's focus can be seen from across a room
 * (Flutter only tints it). Select, Enter and a tap click it, on key up.
 */
@Composable
internal fun FlutterButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val shape = CircleShape
    Box(
        modifier = modifier
            .shadow(elevation = 1.dp, shape = shape)
            .background(if (focused) FlutterColors.Primary else FlutterColors.SurfaceContainerLow, shape)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = FlutterText.LabelLarge,
            color = if (focused) FlutterColors.OnPrimary else FlutterColors.Primary,
        )
    }
}

/**
 * Flutter's app bar `IconButton`: a 24 dp icon in a 48 dp target. Focused, it sits on a solid
 * primary disc (the TV focus highlight; Flutter only tints it).
 */
@Composable
internal fun FlutterIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .padding(4.dp)
            .background(if (focused) FlutterColors.Primary else Color.Transparent, CircleShape)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) FlutterColors.OnPrimary else FlutterColors.OnSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Flutter's `AlertDialog` with one action: a 28 dp rounded card on `surfaceContainerHigh`, as wide
 * as its text (at least 280 dp, 40 dp clear of the screen edges), over a 54% black barrier, the
 * button at the bottom right and focused.
 */
@Composable
internal fun FlutterAlertDialog(title: String, message: String, action: String, onAction: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onAction, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(FlutterColors.SCRIM_ALPHA) }
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp, vertical = 24.dp)
                .widthIn(min = 280.dp)
                .width(IntrinsicSize.Max)
                .background(FlutterColors.SurfaceContainerHigh, RoundedCornerShape(28.dp))
                .padding(24.dp),
        ) {
            Text(text = title, style = FlutterText.HeadlineSmall, color = FlutterColors.OnSurface)
            Spacer(Modifier.height(16.dp))
            Text(text = message, style = FlutterText.BodyMedium, color = FlutterColors.OnSurfaceVariant)
            // 24 dp, then the 4 dp around Flutter's 40 dp button in its 48 dp target.
            Spacer(Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.End) {
                FlutterButton(
                    text = action,
                    onClick = onAction,
                    modifier = Modifier.focusRequester(focus).testTag(TestTags.MESSAGE_ACTION),
                )
            }
        }
    }
    LaunchedEffect(title, message) { focus.requestFocus() }
}

/** Flutter's `CircularProgressIndicator`: a 36 dp spinning arc, 4 dp thick, in the primary colour. */
@Composable
internal fun Spinner(modifier: Modifier = Modifier) {
    val angle by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_333, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(modifier = modifier.size(36.dp).padding(2.dp).testTag(TestTags.SPINNER)) {
        drawArc(
            brush = SolidColor(FlutterColors.Primary),
            startAngle = angle - 90f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Butt),
        )
    }
}

/** Material Icons `logout` (Apache 2.0), Flutter's `Icons.logout` on the setup app bar. */
internal val LogoutIcon: ImageVector by lazy {
    ImageVector.Builder(name = "logout", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser()
                .parsePathString(
                    "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5zM4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z",
                )
                .toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}
