package pro.joaosousa.queuedisplay.tv.domain.model

/**
 * `devices/{uid}` as the TV reads it. Only the portal sets [siteId], [screenId], [name]
 * and [identifyUntilEpochMillis]. The TV reads no `orgId` (C10).
 */
data class Device(
    val uid: String,
    /** Null = never claimed or unassigned: the box shows the pairing screen (TV invariant 8). */
    val siteId: String?,
    /** Null with a [siteId] = show the site's `defaultScreenId` (TV invariant 8). */
    val screenId: String?,
    val name: String?,
    /** When in the future, the box flashes its identity banner. */
    val identifyUntilEpochMillis: Long?,
) {
    val isClaimed: Boolean get() = siteId != null

    /** The screen this box shows: its own, else the site's default. */
    fun effectiveScreenId(site: Site?): String? = screenId ?: site?.defaultScreenId
}

/** What the TV writes into `devices/{uid}.info` (contract `deviceInfoSchema`). */
data class DeviceInfo(
    val model: String,
    val sdkInt: Int,
    /** Display string such as `0.6.0 (21)`. The portal filters templates on [appVersionCode]. */
    val appVersion: String,
    val appVersionCode: Long,
    val heartbeatSec: Int = DEFAULT_HEARTBEAT_SEC,
    val decoders: List<String>,
    /** `WIDTHxHEIGHT`. */
    val maxDisplay: String,
) {
    companion object {
        /** C5: one heartbeat every 10 minutes. */
        const val DEFAULT_HEARTBEAT_SEC = 600
    }
}
