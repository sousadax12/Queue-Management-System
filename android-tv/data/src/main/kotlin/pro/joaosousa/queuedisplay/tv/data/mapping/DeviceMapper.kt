package pro.joaosousa.queuedisplay.tv.data.mapping

import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.DeviceInfo

/**
 * `devices/{uid}` → [Device]. Reads only what the portal assigns. `info`, `status` and
 * `lastSeenAt` are the TV's own writes, and `orgId` and the audit fields are ignored (C9, C10).
 */
object DeviceMapper {
    fun map(uid: String, data: Map<String, Any?>): Mapped<Device> {
        val issues = mutableListOf<ContractIssue>()
        val f = Fields.of(data, Collections.device(uid), issues)
        val siteId = f.string("siteId")?.takeIf { it.isNotEmpty() }
        val screenId = f.string("screenId")?.takeIf { it.isNotEmpty() }
        if (screenId != null && siteId == null) {
            f.issue("screenId", "set without a siteId; the box stays unclaimed")
        }
        val device = Device(
            uid = uid,
            siteId = siteId,
            screenId = screenId.takeIf { siteId != null },
            name = f.string("name"),
            identifyUntilEpochMillis = f.epochMillis("identifyUntil"),
        )
        return Mapped(device, issues)
    }
}

/** The TV's own writes to `devices/{uid}`. */
object DeviceWrites {
    /** Keys the TV may write; the rules reject a create with any portal-owned key. */
    val TV_KEYS = setOf("info", "status", "lastSeenAt")

    /**
     * The doc the TV creates (contract `deviceCreateSchema`): only [TV_KEYS].
     * [serverTimestamp] is `FieldValue.serverTimestamp()` in production.
     */
    fun create(info: DeviceInfo, freeBytes: Long, serverTimestamp: Any): Map<String, Any?> = mapOf(
        "info" to info(info),
        "status" to mapOf(
            "nowPlaying" to null,
            "cacheBytes" to 0L,
            "freeBytes" to freeBytes.coerceAtLeast(0L),
            "lastSyncAt" to null,
            "pendingItems" to 0L,
            "lastError" to null,
        ),
        "lastSeenAt" to serverTimestamp,
    )

    fun info(info: DeviceInfo): Map<String, Any?> = mapOf(
        "model" to info.model,
        "sdkInt" to info.sdkInt.toLong(),
        "appVersion" to info.appVersion,
        "appVersionCode" to info.appVersionCode,
        "heartbeatSec" to info.heartbeatSec.toLong(),
        "decoders" to info.decoders,
        "maxDisplay" to info.maxDisplay,
    )
}
