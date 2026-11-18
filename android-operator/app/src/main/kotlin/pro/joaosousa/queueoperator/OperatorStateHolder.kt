package pro.joaosousa.queueoperator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pro.joaosousa.queuedisplay.tv.data.repository.AuthRepository
import pro.joaosousa.queuedisplay.tv.data.repository.ConfigRepository
import pro.joaosousa.queuedisplay.tv.data.repository.QueueRepository
import pro.joaosousa.queuedisplay.tv.data.repository.RssRepository
import pro.joaosousa.queueoperator.ui.OperatorEvent
import pro.joaosousa.queueoperator.ui.OperatorScreen
import pro.joaosousa.queueoperator.ui.OperatorUiState
import pro.joaosousa.queueoperator.ui.ScreenOption
import pro.joaosousa.queueoperator.ui.SiteOption

/**
 * The operator flow's state machine: setup -> login -> home.
 *
 * Deliberately **not** an Android `ViewModel`: it is a plain class over the four
 * frozen repository interfaces, so the whole flow is exercised in a JVM test
 * with fakes instead of against Firebase. [MainActivity] gives it the activity's
 * scope, so it dies with the screen exactly as a ViewModel would.
 *
 * All platform access is injected ([clock], [scope]) for the same reason.
 */
class OperatorStateHolder(
    private val auth: AuthRepository,
    private val config: ConfigRepository,
    private val queues: QueueRepository,
    private val rss: RssRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Injectable so a JVM test is not stuck advancing an endless clock loop. */
    private val clockTickMillis: Long = CLOCK_TICK_MILLIS,
) {

    private val _state = MutableStateFlow(OperatorUiState(busy = true))
    val state: StateFlow<OperatorUiState> = _state.asStateFlow()

    /** Site-scoped subscriptions (queues, ticker): rebuilt on every site change. */
    private var live: Job? = null

    init {
        watchSites()
        watchClock()
    }

    fun onEvent(event: OperatorEvent) {
        when (event) {
            is OperatorEvent.SelectSite -> selectSite(event.siteId)
            is OperatorEvent.SelectScreen -> _state.update { it.copy(selectedScreenId = event.screenId) }
            OperatorEvent.ConfirmSetup -> confirmSetup()
            OperatorEvent.ContinueAnonymously -> signInAnonymously()
            is OperatorEvent.EmailChanged -> _state.update { it.copy(email = event.value) }
            is OperatorEvent.PasswordChanged -> _state.update { it.copy(password = event.value) }
            OperatorEvent.SignIn -> signIn()
            OperatorEvent.SignOut -> signOut()
            is OperatorEvent.SelectQueue -> _state.update { it.copy(selectedQueueId = event.queueId) }
            OperatorEvent.AdvanceNumber -> advanceNumber()
            OperatorEvent.Retry -> retry()
        }
    }

    private fun watchSites() {
        scope.launch {
            config.sites()
                .catch { throwable -> _state.update { it.copy(busy = false, error = throwable.message ?: NOT_REACHABLE) } }
                .collect { sites ->
                    _state.update { current ->
                        // Keep a selection that still exists, so a snapshot update does not
                        // bounce the clerk back to an empty picker.
                        val stillThere = current.selectedSiteId?.takeIf { id -> sites.any { it.id == id } }
                        current.copy(
                            sites = sites.map { SiteOption(it.id, it.name) },
                            selectedSiteId = stillThere,
                            busy = false,
                            error = null,
                        )
                    }
                }
        }
    }

    private fun watchClock() {
        scope.launch {
            while (isActive) {
                _state.update { it.copy(nowMillis = clock()) }
                delay(clockTickMillis)
            }
        }
    }

    private fun selectSite(siteId: String) {
        live?.cancel()
        _state.update { it.copy(selectedSiteId = siteId, screens = emptyList(), selectedScreenId = null) }
        scope.launch { offerScreens(siteId) }
    }

    /**
     * The picker offers the site's **default screen**, because that is the only
     * screen the frozen `ConfigRepository` can name for a site (it reads one
     * screen by id). Offering more would mean inventing ids.
     */
    private suspend fun offerScreens(siteId: String) {
        val site = runCatching { config.site(siteId).first() }.getOrNull() ?: return
        val screenId = site.defaultScreenId ?: return
        val screen = runCatching { config.screen(siteId, screenId).first() }.getOrNull() ?: return
        _state.update {
            it.copy(
                screens = listOf(ScreenOption(screenId, screen.screen.name)),
                selectedScreenId = screenId,
            )
        }
    }

    private fun confirmSetup() {
        if (_state.value.selectedSiteId == null) {
            _state.update { it.copy(error = "Escolhe um local antes de continuar.") }
            return
        }
        _state.update { it.copy(screen = OperatorScreen.LOGIN, error = null) }
    }

    private fun signInAnonymously() {
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            runCatching { auth.signInAnonymously() }
                .onSuccess { enterHome() }
                .onFailure { throwable -> _state.update { it.copy(busy = false, error = throwable.message ?: SIGN_IN_FAILED) } }
        }
    }

    private fun signIn() {
        val current = _state.value
        if (current.email.isBlank() || current.password.isEmpty()) {
            _state.update { it.copy(error = "Preenche o email e a palavra-passe.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            auth.signIn(current.email, current.password)
                .onSuccess { identity -> enterHome(identity) }
                .onFailure { _state.update { it.copy(busy = false, error = SIGN_IN_FAILED) } }
        }
    }

    private fun signOut() {
        live?.cancel()
        scope.launch { runCatching { auth.signOut() } }
        _state.update {
            OperatorUiState(busy = false, nowMillis = it.nowMillis, sites = it.sites)
        }
    }

    private fun enterHome(identity: pro.joaosousa.queuedisplay.tv.domain.model.OperatorIdentity? = null) {
        _state.update {
            it.copy(screen = OperatorScreen.HOME, busy = false, error = null, password = "", identity = identity ?: it.identity)
        }
        startLive()
    }

    private fun startLive() {
        val siteId = _state.value.selectedSiteId ?: return
        live?.cancel()
        live = scope.launch {
            launch {
                queues.queues(siteId)
                    .catch { throwable -> _state.update { it.copy(error = throwable.message ?: NOT_REACHABLE) } }
                    .collect { list ->
                        _state.update { current ->
                            current.copy(
                                queues = list,
                                // First paint selects the first queue so the big number is never blank.
                                selectedQueueId = current.selectedQueueId
                                    ?.takeIf { id -> list.any { it.id == id } }
                                    ?: list.firstOrNull()?.id,
                            )
                        }
                    }
            }
            launch { watchTicker(siteId) }
        }
    }

    private suspend fun watchTicker(siteId: String) {
        val site = runCatching { config.site(siteId).first() }.getOrNull() ?: return
        val url = site.ticker?.feedUrls?.firstOrNull() ?: return
        rss.feedPolled(url, site.ticker?.refreshMinutes ?: DEFAULT_REFRESH_MINUTES)
            .catch { throwable -> _state.update { it.copy(tickerError = throwable.message ?: TICKER_FAILED) } }
            .collect { result ->
                result
                    .onSuccess { feed -> _state.update { it.copy(ticker = feed.items, tickerError = null) } }
                    // A dead feed must not hide a working counter behind an error banner.
                    .onFailure { throwable -> _state.update { it.copy(tickerError = throwable.message ?: TICKER_FAILED) } }
            }
    }

    private fun advanceNumber() {
        val current = _state.value
        val queue = current.queues.firstOrNull { it.id == current.selectedQueueId } ?: return
        scope.launch {
            queues.setNumber(queue.id, queue.number + 1)
                .onFailure { throwable -> _state.update { it.copy(error = throwable.message ?: ADVANCE_FAILED) } }
        }
    }

    private fun retry() {
        _state.update { it.copy(error = null, tickerError = null) }
        if (_state.value.screen == OperatorScreen.HOME) startLive() else watchSites()
    }

    private companion object {
        const val CLOCK_TICK_MILLIS = 1_000L
        const val DEFAULT_REFRESH_MINUTES = 15L
        const val NOT_REACHABLE = "Sem ligação. Verifica a rede e tenta de novo."
        const val SIGN_IN_FAILED = "Não foi possível entrar. Verifica o email e a palavra-passe."
        const val ADVANCE_FAILED = "Não foi possível avançar o número."
        const val TICKER_FAILED = "Não foi possível ler o feed de notícias."
    }
}
