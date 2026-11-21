package pro.joaosousa.queuedisplay.operator.queues

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Replaces Firestore, DataStore and SoundPool in Robolectric tests. Tests `@Inject` the fakes. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [QueuesModule::class])
object FakeQueuesModule {

    @Provides
    @Singleton
    fun fakeSource(): FakeQueueSource = FakeQueueSource.fromFixtures()

    @Provides
    fun source(fake: FakeQueueSource): QueueSource = fake

    @Provides
    @Singleton
    fun fakeStore(): FakeSelectedQueueStore = FakeSelectedQueueStore()

    @Provides
    fun store(fake: FakeSelectedQueueStore): SelectedQueueStore = fake

    @Provides
    @Singleton
    fun fakeChime(): FakeChime = FakeChime()

    @Provides
    fun chime(fake: FakeChime): Chime = fake
}
