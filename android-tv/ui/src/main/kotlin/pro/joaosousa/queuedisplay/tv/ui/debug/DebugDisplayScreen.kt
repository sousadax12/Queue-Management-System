package pro.joaosousa.queuedisplay.tv.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.tv.domain.BuildInfo
import pro.joaosousa.queuedisplay.tv.domain.display.DisplayState
import pro.joaosousa.queuedisplay.tv.domain.display.PlaylistState
import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.ui.theme.QueueDisplayTheme

private val Warning = Color(0xFFFFB74D)

/**
 * M2 debug screen: what the box read from Firestore, with the site's queues and their live
 * numbers. Templates replace it from M3.
 */
@Composable
fun DebugDisplayScreen(
    state: DisplayState,
    appName: String,
    buildInfo: BuildInfo,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            Text("$appName · ${buildInfo.label}", style = MaterialTheme.typography.titleMedium, color = Muted)
            Spacer(Modifier.height(16.dp))
            when (state) {
                DisplayState.SigningIn -> Text("Signing in…", style = MaterialTheme.typography.headlineMedium)
                is DisplayState.Failed -> {
                    Text("Can't read the display config", style = MaterialTheme.typography.headlineMedium, color = Warning)
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                    Text("Retrying in ${state.retryInMillis / 1000} s", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                is DisplayState.Unclaimed -> Unclaimed(state)
                is DisplayState.Claimed -> Claimed(state)
            }
        }
    }
}

private val Muted @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

@Composable
private fun Unclaimed(state: DisplayState.Unclaimed) {
    Text("Not claimed yet", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text("Device id", style = MaterialTheme.typography.bodyMedium, color = Muted)
    Text(state.uid, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
    Spacer(Modifier.height(16.dp))
    Text(
        "Create a pairing code for this id and claim it (android-tv/README.md, \"Firestore emulator\").",
        style = MaterialTheme.typography.bodyMedium,
        color = Muted,
    )
}

@Composable
private fun Claimed(state: DisplayState.Claimed) {
    val site = state.site
    Line("Site", site?.let { "${it.name} (${it.id})" } ?: "${state.siteId} (not found)")
    Line("Device", state.device.name ?: state.uid)
    val screen = state.screen
    Line(
        "Screen",
        when {
            state.screenId == null -> "none: no screenId and no site defaultScreenId"
            screen == null -> "${state.screenId} (not found)"
            else -> "${screen.name} (${screen.id}) · ${screen.template.wire}"
        },
    )
    screen?.templateFallback?.let { fallback ->
        Line("Template", "\"${fallback.raw ?: "missing"}\" is unknown; showing ${screen.template.wire}", Warning)
    }
    when (val p = state.playlist) {
        PlaylistState.None -> Line("Playlist", "none")
        is PlaylistState.Loaded -> Line("Playlist", p.playlist?.let { "${it.name} · ${it.items.size} items" } ?: "not found")
        is PlaylistState.Failed -> Line("Playlist", p.message, Warning)
    }
    Spacer(Modifier.height(24.dp))

    val onScreen = screen?.zones?.queues?.select(state.queues)?.map { it.id }?.toSet().orEmpty()
    if (state.queues.isEmpty()) {
        Text("No queues in this site", style = MaterialTheme.typography.headlineSmall, color = Muted)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.queues.forEach { QueueRow(it, onScreen = it.id in onScreen) }
    }
}

@Composable
private fun Line(label: String, value: String, color: Color = Color.Unspecified) {
    Row {
        Text(label, modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodyLarge, color = Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun QueueRow(queue: Queue, onScreen: Boolean) {
    val foreground = Color(queue.foregroundColor.toInt())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(queue.backgroundColor.toInt()), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(queue.name.ifEmpty { queue.id }, style = MaterialTheme.typography.headlineMedium, color = foreground)
            Text(
                if (onScreen) "${queue.id} · on this screen" else queue.id,
                style = MaterialTheme.typography.bodyMedium,
                color = foreground.copy(alpha = 0.7f),
            )
        }
        Text(
            queue.number.toString(),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = foreground,
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun DebugDisplayScreenPreview() {
    QueueDisplayTheme {
        DebugDisplayScreen(
            state = DisplayState.Claimed(
                uid = "uid",
                device = Device("uid", "site_porto", null, "Box 3", null),
                site = null,
                screenId = null,
                screen = null,
                playlist = PlaylistState.None,
                queues = listOf(
                    Queue("q1", "site_porto", "Balcão A", 42, 0xf007, 0xff3f51b5L, 0xffffffffL, "", 0.0, 1.0, 60.0),
                ),
            ),
            appName = "Queue Display",
            buildInfo = BuildInfo("0.1.0", 1, "debug"),
        )
    }
}
