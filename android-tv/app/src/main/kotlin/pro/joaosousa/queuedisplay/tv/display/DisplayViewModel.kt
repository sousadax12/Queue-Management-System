package pro.joaosousa.queuedisplay.tv.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pro.joaosousa.queuedisplay.tv.device.DeviceFacts
import pro.joaosousa.queuedisplay.tv.domain.display.DisplayState
import pro.joaosousa.queuedisplay.tv.domain.display.ObserveDisplay

@HiltViewModel
class DisplayViewModel @Inject constructor(
    observeDisplay: ObserveDisplay,
    facts: DeviceFacts,
) : ViewModel() {

    val state: StateFlow<DisplayState> = observeDisplay(facts.info(), facts::freeBytes)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DisplayState.SigningIn)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
