package pro.joaosousa.queuedisplay.operator.ticker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pro.joaosousa.queuedisplay.tv.domain.repository.HeadlineRepository

/** The RTP headlines for home's ticker (design F10, Q12: the feed stays hard-coded). */
@HiltViewModel
class TickerViewModel @Inject constructor(repository: HeadlineRepository) : ViewModel() {

    /** Empty hides the ticker. Fetched while the app is visible, every 15 minutes. */
    val headlines: StateFlow<List<String>> = repository.headlines(HeadlineRepository.RTP_FEED_URL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
