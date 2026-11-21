package pro.joaosousa.queuedisplay.tv.domain.display

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.DeviceInfo
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.repository.ConfigRepository
import pro.joaosousa.queuedisplay.tv.domain.repository.DeviceRepository

/**
 * The read path from start-up to live queue numbers: sign in anonymously, make sure
 * `devices/{uid}` exists, then follow the device to its site, screen, playlist and the site's
 * queues. The 5 listeners match the design's budget. A failure shows as [DisplayState.Failed]
 * and the whole chain restarts after a backoff.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDisplay(
    private val devices: DeviceRepository,
    private val config: ConfigRepository,
    private val backoffMillis: (attempt: Long) -> Long = ::defaultBackoffMillis,
) {
    operator fun invoke(info: DeviceInfo, freeBytes: () -> Long): Flow<DisplayState> = flow {
        emit(DisplayState.SigningIn)
        val uid = devices.signIn()
        devices.registerIfMissing(uid, info, freeBytes())
        emitAll(
            devices.device(uid)
                // Heartbeat writes don't change the mapped Device, so they don't restart listeners.
                .distinctUntilChanged()
                .flatMapLatest { device ->
                    if (device == null || !device.isClaimed) flowOf(DisplayState.Unclaimed(uid)) else claimed(uid, device)
                },
        )
    }.retryWhen { cause, attempt ->
        if (cause is CancellationException) return@retryWhen false
        val wait = backoffMillis(attempt)
        emit(DisplayState.Failed(cause.message ?: cause::class.simpleName.orEmpty(), wait))
        delay(wait)
        true
    }

    private fun claimed(uid: String, device: Device): Flow<DisplayState> {
        val siteId = checkNotNull(device.siteId)
        val site = config.site(siteId)
        val screen = site
            .map { device.effectiveScreenId(it) }
            .distinctUntilChanged()
            .flatMapLatest { screenId -> screenView(siteId, screenId) }
        return combine(site, screen, config.queues(siteId)) { s, view, queues ->
            DisplayState.Claimed(
                uid = uid,
                device = device,
                site = s,
                screenId = view.screenId,
                screen = view.screen,
                playlist = view.playlist,
                queues = queues,
            )
        }
    }

    private data class ScreenView(val screenId: String?, val screen: Screen?, val playlist: PlaylistState)

    private fun screenView(siteId: String, screenId: String?): Flow<ScreenView> {
        if (screenId == null) return flowOf(ScreenView(null, null, PlaylistState.None))
        val screen = config.screen(siteId, screenId)
        val playlist = screen
            .map { it?.zones?.media?.playlistId }
            .distinctUntilChanged()
            .flatMapLatest { playlistId ->
                if (playlistId == null) {
                    flowOf(PlaylistState.None)
                } else {
                    config.playlist(playlistId)
                        .map<_, PlaylistState> { PlaylistState.Loaded(it) }
                        .catch { e -> emit(PlaylistState.Failed(e.message ?: e::class.simpleName.orEmpty())) }
                }
            }
        return combine(screen, playlist) { s, p -> ScreenView(screenId, s, p) }
    }

    companion object {
        /** 2 s, 4 s, 8 s ... capped at 60 s. */
        fun defaultBackoffMillis(attempt: Long): Long = (2_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(60_000L)
    }
}
