package pro.joaosousa.queuedisplay.tv

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pro.joaosousa.queuedisplay.tv.data.DataModule
import pro.joaosousa.queuedisplay.tv.domain.model.Device
import pro.joaosousa.queuedisplay.tv.domain.model.DeviceInfo
import pro.joaosousa.queuedisplay.tv.domain.model.Playlist
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.Site
import pro.joaosousa.queuedisplay.tv.domain.repository.ConfigRepository
import pro.joaosousa.queuedisplay.tv.domain.repository.DeviceRepository

/** Replaces Firebase in Robolectric tests: an unclaimed box with no backend. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object FakeDataModule {
    const val UID = "test-uid"

    @Provides
    fun deviceRepository(): DeviceRepository = object : DeviceRepository {
        override suspend fun signIn() = UID
        override suspend fun registerIfMissing(uid: String, info: DeviceInfo, freeBytes: Long) = Unit
        override fun device(uid: String): Flow<Device?> = flowOf(null)
    }

    @Provides
    fun configRepository(): ConfigRepository = object : ConfigRepository {
        override fun site(siteId: String): Flow<Site?> = flowOf(null)
        override fun screen(siteId: String, screenId: String): Flow<Screen?> = flowOf(null)
        override fun playlist(playlistId: String): Flow<Playlist?> = flowOf(null)
        override fun queues(siteId: String): Flow<List<Queue>> = flowOf(emptyList())
    }
}
