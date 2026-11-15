package pro.joaosousa.queuedisplay.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.tv.domain.BuildInfo
import pro.joaosousa.queuedisplay.tv.ui.theme.QueueDisplayTheme

/** Full-screen placeholder shown until templates arrive (M3): app name and build version. */
@Composable
fun DisplayPlaceholder(
    appName: String,
    buildInfo: BuildInfo,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = appName, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                text = buildInfo.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun DisplayPlaceholderPreview() {
    QueueDisplayTheme {
        DisplayPlaceholder(
            appName = "Queue Display",
            buildInfo = BuildInfo(versionName = "0.1.0", versionCode = 1, buildType = "debug"),
        )
    }
}
