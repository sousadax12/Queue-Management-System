package pro.joaosousa.queuedisplay.tv.data.repository

import kotlinx.coroutines.flow.Flow
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.Site

/** `sites` and their `screens`, read-only. The portal is the only writer. */
interface ConfigRepository {

    fun sites(): Flow<List<Site>>

    fun site(siteId: String): Flow<Site?>

    /**
     * `sites/{siteId}/screens/{screenId}`, and the unknown-template report from
     * `ScreenMapper` (invariant 5) is surfaced here so the app can log it.
     */
    fun screen(siteId: String, screenId: String): Flow<ScreenResult?>
}

/** A screen plus whether its `template` was one this build does not know. */
data class ScreenResult(val screen: Screen, val unknownTemplate: String?)
