package pro.joaosousa.queuedisplay.operator.queues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pro.joaosousa.queuedisplay.operator.session.OperatorSession
import pro.joaosousa.queuedisplay.operator.session.SessionState
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.operator.QueueNumber

/** Which queue this box operates. */
sealed interface Selection {
    /** Not read from the store yet. */
    data object Unknown : Selection

    data object None : Selection

    data class Selected(val queueId: String) : Selection
}

/** What the signed-in screens render. */
data class QueuesUi(
    val queues: QueueListState = QueueListState.Loading,
    val selection: Selection = Selection.Unknown,
    /** The last write the server rejected, shown for a few seconds on home. */
    val writeError: QueueError? = null,
) {
    enum class Route { Loading, Setup, Home }

    val selectedQueue: Queue?
        get() {
            val id = (selection as? Selection.Selected)?.queueId ?: return null
            return (queues as? QueueListState.Data)?.queues?.firstOrNull { it.id == id }
        }

    /** A remembered queue that the live list no longer has (deleted, or out of scope). */
    val selectionIsGone: Boolean
        get() = selection is Selection.Selected && queues is QueueListState.Data && selectedQueue == null

    val route: Route
        get() = when {
            selection == Selection.Unknown -> Route.Loading
            selection == Selection.None || selectionIsGone -> Route.Setup
            else -> Route.Home
        }
}

/**
 * Setup, home and advancing (design F5-F8, "Architecture: Advancing").
 *
 * One live listener on the scope's queues feeds both screens. A press steps the selected queue's
 * **last known** number (the listener's value, or this box's own latest press if the listener
 * hasn't caught up yet) with the 99↔0 wrap, shows it at once, plays the chime and writes it with a
 * plain `update({number})`. Offline, the write waits in Firestore's cache and syncs on reconnect;
 * concurrent writers are last writer wins. Everything runs on the main thread, so presses apply in
 * order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QueuesViewModel @Inject constructor(
    session: OperatorSession,
    private val repository: OperatorQueueRepository,
    private val store: SelectedQueueStore,
    private val chime: Chime,
) : ViewModel() {

    private val remote = MutableStateFlow<QueueListState>(QueueListState.Loading)
    private val selection = MutableStateFlow<Selection>(Selection.Unknown)
    private val writeError = MutableStateFlow<QueueError?>(null)

    /** This box's presses that the listener hasn't reported back yet, by queue id. */
    private val pending = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val retries = MutableStateFlow(0)
    private var writeErrorTimeout: Job? = null

    val ui: StateFlow<QueuesUi> = combine(remote, pending, selection, writeError) { list, local, sel, error ->
        QueuesUi(queues = list.withLocal(local), selection = sel, writeError = error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, QueuesUi())

    init {
        val scope = session.state.map { (it as? SessionState.Ready)?.scope }.distinctUntilChanged()
        viewModelScope.launch {
            combine(scope, retries) { s, _ -> s }
                .flatMapLatest { s -> if (s == null) flowOf(QueueListState.Loading) else repository.observe(s) }
                .collect { list ->
                    // The listener now includes every write made so far (latency compensation).
                    if (list is QueueListState.Data) pending.value = emptyMap()
                    remote.value = list
                }
        }
        viewModelScope.launch {
            store.selected.collect { id -> selection.value = id?.let(Selection::Selected) ?: Selection.None }
        }
        viewModelScope.launch {
            // Signing out (or losing access) forgets the queue, so the next account starts at setup.
            session.state.collect { state ->
                if (state is SessionState.SignedOut || state is SessionState.NoAccess) store.clear()
            }
        }
        viewModelScope.launch {
            ui.collect { if (it.selectionIsGone) store.clear() }
        }
    }

    /** Setup: operate [queueId] from now on, also after a restart. */
    fun select(queueId: String) {
        selection.value = Selection.Selected(queueId)
        clearWriteError()
        viewModelScope.launch { store.select(queueId) }
    }

    /** Home: Back returns to setup (Flutter exited the app instead, design F13). */
    fun backToSetup() {
        selection.value = Selection.None
        clearWriteError()
        viewModelScope.launch { store.clear() }
    }

    /** Re-subscribes after a failed read. */
    fun retry() {
        retries.update { it + 1 }
    }

    /** D-pad right ([QueueNumber.Direction.Up]) or left ([QueueNumber.Direction.Down]) on home. */
    fun advance(direction: QueueNumber.Direction) {
        val id = (selection.value as? Selection.Selected)?.queueId ?: return
        val queue = (remote.value as? QueueListState.Data)?.queues?.firstOrNull { it.id == id } ?: return
        val number = QueueNumber.step(pending.value[id] ?: queue.number, direction)
        pending.update { it + (id to number) }
        // On every press, whether or not the write succeeds (F8).
        chime.play(queue.soundVolume)
        repository.setNumber(id, number) { error -> showWriteError(error) }
    }

    private fun showWriteError(error: QueueError) {
        writeError.value = error
        writeErrorTimeout?.cancel()
        writeErrorTimeout = viewModelScope.launch {
            delay(WRITE_ERROR_MILLIS)
            writeError.value = null
        }
    }

    private fun clearWriteError() {
        writeErrorTimeout?.cancel()
        writeError.value = null
    }

    private fun QueueListState.withLocal(local: Map<String, Int>): QueueListState =
        if (this !is QueueListState.Data || local.isEmpty()) {
            this
        } else {
            QueueListState.Data(queues.map { q -> local[q.id]?.let { q.copy(number = it) } ?: q })
        }

    companion object {
        const val WRITE_ERROR_MILLIS = 5_000L
    }
}
