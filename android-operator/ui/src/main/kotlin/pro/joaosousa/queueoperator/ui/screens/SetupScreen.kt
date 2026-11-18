package pro.joaosousa.queueoperator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pro.joaosousa.queueoperator.ui.OperatorEvent
import pro.joaosousa.queueoperator.ui.OperatorUiState
import pro.joaosousa.queueoperator.ui.ScreenOption
import pro.joaosousa.queueoperator.ui.SiteOption
import pro.joaosousa.queueoperator.ui.components.ErrorBanner
import pro.joaosousa.queueoperator.ui.components.OperatorTopBar

/**
 * First screen: which site (and, when the config exposes them, which screen) this
 * terminal is bound to.
 *
 * The Flutter `SetupPage` put the queue list here because it had no site concept;
 * the native app resolves a site first (design: "setup (pick site + screen)") and
 * the queue list moves to [HomeScreen], where the clerk actually advances numbers.
 */
@Composable
fun SetupScreen(
    state: OperatorUiState,
    onEvent: (OperatorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        OperatorTopBar(
            title = "Escolher posto",
            subtitle = "Escolha o posto deste terminal",
            onSignOut = { onEvent(OperatorEvent.SignOut) },
        )

        if (state.error != null) {
            ErrorBanner(
                message = state.error,
                onRetry = { onEvent(OperatorEvent.Retry) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.busy && state.sites.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                state.sites.isEmpty() -> Text(
                    text = "Nenhum posto encontrado",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    SectionLabel("Posto")
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.sites, key = SiteOption::id) { site ->
                            ChoiceRow(
                                label = site.name,
                                selected = site.id == state.selectedSiteId,
                                onClick = { onEvent(OperatorEvent.SelectSite(site.id)) },
                            )
                        }
                    }

                    // Sites do not expose their screens through ConfigRepository
                    // yet, so the picker stays hidden until the state carries
                    // options — the app must not claim a choice it cannot offer.
                    if (state.screens.isNotEmpty()) {
                        SectionLabel("Ecrã", modifier = Modifier.padding(top = 12.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.screens, key = ScreenOption::id) { screen ->
                                ChoiceRow(
                                    label = screen.name,
                                    selected = screen.id == state.selectedScreenId,
                                    onClick = { onEvent(OperatorEvent.SelectScreen(screen.id)) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                Button(
                    onClick = { onEvent(OperatorEvent.ConfirmSetup) },
                    enabled = state.selectedSiteId != null && !state.busy,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                ) {
                    Text("Continuar", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/**
 * A full-width selectable row. Cards are rows, not a grid: a counter has a handful
 * of sites and the clerk reads names, so one tap target per line beats tiles.
 */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                shape = shape,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "Selecionado",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}