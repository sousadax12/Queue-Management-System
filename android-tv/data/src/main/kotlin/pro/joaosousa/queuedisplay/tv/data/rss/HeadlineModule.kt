package pro.joaosousa.queuedisplay.tv.data.rss

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pro.joaosousa.queuedisplay.tv.domain.repository.HeadlineRepository

/** The RSS ticker feed. Its own module, so app tests can replace it without touching Firebase. */
@Module
@InstallIn(SingletonComponent::class)
abstract class HeadlineModule {

    @Binds
    abstract fun headlineRepository(impl: RssHeadlineRepository): HeadlineRepository

    @Binds
    abstract fun feedFetcher(impl: HttpFeedFetcher): FeedFetcher
}
