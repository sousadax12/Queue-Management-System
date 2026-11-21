package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.operator.R
import pro.joaosousa.queuedisplay.operator.queues.QueueError

/**
 * What both screens show instead of queues: Flutter's spinner while loading, and a centred line of
 * plain text for a failed read or no queues (Flutter: `Error: …` and "No documents found"). A
 * permission denial reads "Access denied" and never "No queues" (the closed PR #20 mixed them up).
 */
@Composable
internal fun QueueListMessage(kind: QueueListMessageKind, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (kind) {
            QueueListMessageKind.Loading -> Spinner()
            QueueListMessageKind.Empty -> Text(
                stringResource(R.string.queues_empty),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(TestTags.QUEUES_EMPTY),
            )
            is QueueListMessageKind.Failed -> {
                Text(
                    text = errorTitle(kind.error),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(TestTags.QUEUES_ERROR),
                )
                if (kind.error == QueueError.PermissionDenied) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.queues_access_denied_hint), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

internal sealed interface QueueListMessageKind {
    data object Loading : QueueListMessageKind
    data object Empty : QueueListMessageKind
    data class Failed(val error: QueueError) : QueueListMessageKind
}

@Composable
private fun errorTitle(error: QueueError): String = when (error) {
    QueueError.PermissionDenied -> stringResource(R.string.queues_access_denied)
    QueueError.NotFound -> stringResource(R.string.queues_not_found)
    is QueueError.Other -> stringResource(R.string.start_error, error.message)
}

/** A rejected write, as the short message on home. */
@Composable
internal fun writeErrorText(error: QueueError): String = when (error) {
    QueueError.PermissionDenied -> stringResource(R.string.write_access_denied)
    QueueError.NotFound -> stringResource(R.string.write_not_found)
    is QueueError.Other -> stringResource(R.string.write_failed, error.message)
}
