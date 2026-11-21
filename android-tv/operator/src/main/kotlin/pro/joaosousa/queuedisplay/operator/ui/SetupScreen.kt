package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.operator.R
import pro.joaosousa.queuedisplay.operator.queues.QueueListState
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.operator.OperatorScope
import pro.joaosousa.queuedisplay.tv.domain.operator.StaffRole
import pro.joaosousa.queuedisplay.tv.ui.zones.QueueTile

/** Flutter's `Colors.greenAccent`: the highlighted tile (design F5). */
private val Highlight = Color(0xFF69F0AE)

/**
 * The queue picker (design F5, `lib/pages/setup_page.dart`): the scope's queues in one row of
 * 300 dp tiles. D-pad left/right moves the green highlight, select (or Enter, or a tap) picks.
 * Sign-out is above the row. A failed read offers Retry.
 */
@Composable
fun SetupScreen(
    email: String?,
    scope: OperatorScope,
    queues: QueueListState,
    onSelect: (queueId: String) -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
) {
    val signOutFocus = remember { FocusRequester() }
    val firstTileFocus = remember { FocusRequester() }
    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Title(stringResource(R.string.setup_title))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.setup_signed_in_as, email.orEmpty()))
                Text(
                    stringResource(R.string.setup_scope, scope.role.claim, scopeSummary(scope)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (queues is QueueListState.Failed) {
                Button(onClick = onRetry, modifier = Modifier.testTag(TestTags.RETRY)) {
                    Text(stringResource(R.string.retry))
                }
                Spacer(Modifier.width(16.dp))
            }
            Button(
                onClick = onSignOut,
                modifier = Modifier.focusRequester(signOutFocus).testTag(TestTags.SIGN_OUT),
            ) {
                Text(stringResource(R.string.sign_out))
            }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (queues) {
                QueueListState.Loading -> QueueListMessage(QueueListMessageKind.Loading)
                is QueueListState.Failed -> QueueListMessage(QueueListMessageKind.Failed(queues.error))
                is QueueListState.Data ->
                    if (queues.queues.isEmpty()) {
                        QueueListMessage(QueueListMessageKind.Empty)
                    } else {
                        QueueRow(queues.queues, firstTileFocus, onSelect)
                    }
            }
        }
    }
    val hasTiles = (queues as? QueueListState.Data)?.queues?.isNotEmpty() == true
    LaunchedEffect(hasTiles) {
        if (hasTiles) firstTileFocus.requestFocus() else signOutFocus.requestFocus()
    }
}

@Composable
private fun QueueRow(queues: List<Queue>, firstTileFocus: FocusRequester, onSelect: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().testTag(TestTags.SETUP_ROW),
    ) {
        itemsIndexed(queues, key = { _, q -> q.id }) { index, queue ->
            SetupTile(
                queue = queue,
                onSelect = { onSelect(queue.id) },
                modifier = if (index == 0) Modifier.focusRequester(firstTileFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun SetupTile(queue: Queue, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .padding(vertical = 50.dp)
            .width(300.dp)
            .onFocusChanged { focused = it.isFocused }
            // Clickable handles DPAD_CENTER, Enter and NumPad Enter on key up, and taps.
            .clickable(onClick = onSelect)
            .background(if (focused) Highlight else Color.Transparent, shape)
            .border(1.dp, if (focused) Highlight else Color.Gray, shape)
            .padding(8.dp)
            .testTag(TestTags.setupTile(queue.id)),
    ) {
        QueueTile(queue, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun scopeSummary(scope: OperatorScope): String = when (scope.role) {
    StaffRole.PlatformAdmin -> stringResource(R.string.setup_all_orgs)
    StaffRole.OrgAdmin -> stringResource(R.string.setup_whole_org, scope.orgId.orEmpty())
    StaffRole.Manager, StaffRole.Operator ->
        stringResource(R.string.setup_sites, scope.orgId.orEmpty(), scope.sites.joinToString())
}
