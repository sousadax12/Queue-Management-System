package pro.joaosousa.queuedisplay.operator.queues

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The operator's queue seams. Tests replace this module with fakes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class QueuesModule {

    @Binds
    abstract fun queueSource(impl: FirestoreQueueSource): QueueSource

    @Binds
    abstract fun selectedQueueStore(impl: DataStoreSelectedQueueStore): SelectedQueueStore

    @Binds
    abstract fun chime(impl: SoundPoolChime): Chime
}
