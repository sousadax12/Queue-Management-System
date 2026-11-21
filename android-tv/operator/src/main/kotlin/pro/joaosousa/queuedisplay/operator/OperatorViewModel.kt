package pro.joaosousa.queuedisplay.operator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pro.joaosousa.queuedisplay.operator.session.OperatorSession
import pro.joaosousa.queuedisplay.operator.session.SessionState

@HiltViewModel
class OperatorViewModel @Inject constructor(
    private val session: OperatorSession,
) : ViewModel() {

    val state: StateFlow<SessionState> = session.state

    init {
        start()
    }

    fun start() {
        viewModelScope.launch { session.start() }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch { session.signIn(email, password) }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    fun acknowledge() = session.acknowledge()
}
