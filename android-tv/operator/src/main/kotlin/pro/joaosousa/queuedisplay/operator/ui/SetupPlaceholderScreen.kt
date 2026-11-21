package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import pro.joaosousa.queuedisplay.operator.R
import pro.joaosousa.queuedisplay.tv.domain.operator.OperatorScope
import pro.joaosousa.queuedisplay.tv.domain.operator.StaffRole

/** Placeholder for the queue picker (O3): who is signed in, what they may operate, and sign-out. */
@Composable
fun SetupPlaceholderScreen(email: String?, scope: OperatorScope, onSignOut: () -> Unit) {
    val focus = remember { FocusRequester() }
    Centered {
        Title(stringResource(R.string.setup_title))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.setup_signed_in_as, email.orEmpty()))
        Text(stringResource(R.string.setup_scope, scope.role.claim, scopeSummary(scope)))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.setup_placeholder))
        Spacer(Modifier.height(32.dp))
        Button(onClick = onSignOut, modifier = Modifier.focusRequester(focus).testTag(TestTags.SIGN_OUT)) {
            Text(stringResource(R.string.sign_out))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun scopeSummary(scope: OperatorScope): String = when (scope.role) {
    StaffRole.PlatformAdmin -> stringResource(R.string.setup_all_orgs)
    StaffRole.OrgAdmin -> stringResource(R.string.setup_whole_org, scope.orgId.orEmpty())
    StaffRole.Manager, StaffRole.Operator ->
        stringResource(R.string.setup_sites, scope.orgId.orEmpty(), scope.sites.joinToString())
}
