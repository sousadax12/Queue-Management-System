package pro.joaosousa.queueoperator.ui

import pro.joaosousa.queuedisplay.tv.domain.model.FeedItem
import pro.joaosousa.queuedisplay.tv.domain.model.OperatorIdentity
import pro.joaosousa.queuedisplay.tv.domain.model.Queue

/** The three screens the Flutter app has today: setup, login, home. */
enum class OperatorScreen { SETUP, LOGIN, HOME }

data class SiteOption(val id: String, val name: String)
data class ScreenOption(val id: String, val name: String)

/**
 * Everything the operator UI renders. Frozen so the screens and the state
 * holders can be written independently: `:ui` never sees a repository.
 */
data class OperatorUiState(
    val screen: OperatorScreen = OperatorScreen.SETUP,
    val busy: Boolean = false,
    val error: String? = null,
    val sites: List<SiteOption> = emptyList(),
    val selectedSiteId: String? = null,
    val screens: List<ScreenOption> = emptyList(),
    val selectedScreenId: String? = null,
    val identity: OperatorIdentity? = null,
    val queues: List<Queue> = emptyList(),
    val selectedQueueId: String? = null,
    val ticker: List<FeedItem> = emptyList(),
    /** `System.currentTimeMillis()`, injected so the clock widget stays pure. */
    val nowMillis: Long = 0L,
    /**
     * The login form's draft values. They live in the state (not in the screen)
     * because [OperatorEvent.EmailChanged] / [OperatorEvent.PasswordChanged] are
     * part of the frozen contract, so the state holder is their only owner.
     * Transient: never logged, never persisted.
     */
    val email: String = "",
    val password: String = "",
    /**
     * Why the RSS ticker is empty, when it failed. Separate from [error] so a
     * dead feed does not hide a working screen behind an error banner (the
     * ticker degrades, the counter keeps running).
     */
    val tickerError: String? = null,
)

sealed interface OperatorEvent {
    data class SelectSite(val siteId: String) : OperatorEvent
    data class SelectScreen(val screenId: String) : OperatorEvent
    data object ConfirmSetup : OperatorEvent
    data object ContinueAnonymously : OperatorEvent
    data class EmailChanged(val value: String) : OperatorEvent
    data class PasswordChanged(val value: String) : OperatorEvent
    data object SignIn : OperatorEvent
    data object SignOut : OperatorEvent
    data class SelectQueue(val queueId: String) : OperatorEvent
    /** Advance by one, like the Flutter operator's tap. */
    data object AdvanceNumber : OperatorEvent
    data object Retry : OperatorEvent
}
