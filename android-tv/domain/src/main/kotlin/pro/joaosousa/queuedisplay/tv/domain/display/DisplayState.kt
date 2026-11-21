package pro.joaosousa.queuedisplay.tv.domain.display

import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.Playlist
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.Site

/** Everything the box knows about what to show, built from its live Firestore docs. */
sealed interface DisplayState {
    data object SigningIn : DisplayState

    /** No `siteId` on `devices/{uid}`: the box waits to be claimed (TV invariant 8). */
    data class Unclaimed(val uid: String) : DisplayState

    data class Claimed(
        val uid: String,
        val device: Device,
        /** Null until the site doc arrives, or if it doesn't exist. */
        val site: Site?,
        /** The device's screen, else the site's default; null if neither is set. */
        val screenId: String?,
        val screen: Screen?,
        val playlist: PlaylistState,
        /** Every queue of the site, ordered by name. */
        val queues: List<Queue>,
    ) : DisplayState {
        val siteId: String get() = checkNotNull(device.siteId)
    }

    /** Something failed (offline at first start, rules not deployed, ...). It retries on its own. */
    data class Failed(val message: String, val retryInMillis: Long) : DisplayState
}

sealed interface PlaylistState {
    /** The screen has no playlist. */
    data object None : PlaylistState

    /** [playlist] is null while the doc is missing. */
    data class Loaded(val playlist: Playlist?) : PlaylistState

    /** The playlist can't be read, e.g. it belongs to another site. The rest of the screen still works. */
    data class Failed(val message: String) : PlaylistState
}
