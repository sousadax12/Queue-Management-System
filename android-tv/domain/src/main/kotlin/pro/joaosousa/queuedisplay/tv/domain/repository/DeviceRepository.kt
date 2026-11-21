package pro.joaosousa.queuedisplay.tv.domain.repository

import kotlinx.coroutines.flow.Flow
import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.DeviceInfo

/** The box's identity: its anonymous user and its `devices/{uid}` doc. */
interface DeviceRepository {
    /** Signs in anonymously unless a user is already cached, and returns the uid (the device id). */
    suspend fun signIn(): String

    /**
     * Creates `devices/{uid}` with only `info`, `status` and `lastSeenAt` when the server has
     * no such doc. Never touches an existing doc, so it can't clear a claim.
     */
    suspend fun registerIfMissing(uid: String, info: DeviceInfo, freeBytes: Long)

    /** The device doc, live. Emits null while it doesn't exist. */
    fun device(uid: String): Flow<Device?>
}
