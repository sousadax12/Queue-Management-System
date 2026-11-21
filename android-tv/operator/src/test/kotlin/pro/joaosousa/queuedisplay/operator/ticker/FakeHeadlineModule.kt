package pro.joaosousa.queuedisplay.operator.ticker

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import pro.joaosousa.queuedisplay.tv.data.rss.HeadlineModule
import pro.joaosousa.queuedisplay.tv.domain.repository.HeadlineRepository

/** Headlines a test sets by hand. Starts empty: the ticker is hidden. */
class FakeHeadlineRepository : HeadlineRepository {
    val headlines = MutableStateFlow(emptyList<String>())

    override fun headlines(feedUrl: String): Flow<List<String>> = headlines
}

/** Keeps the Robolectric tests off the network. Tests `@Inject` the fake. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [HeadlineModule::class])
object FakeHeadlineModule {

    @Provides
    @Singleton
    fun fake(): FakeHeadlineRepository = FakeHeadlineRepository()

    @Provides
    fun repository(fake: FakeHeadlineRepository): HeadlineRepository = fake
}
