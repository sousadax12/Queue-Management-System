package pro.joaosousa.queuedisplay.tv.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pro.joaosousa.queuedisplay.tv.BuildConfig
import pro.joaosousa.queuedisplay.tv.domain.BuildInfo

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideBuildInfo(): BuildInfo = BuildInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        buildType = BuildConfig.BUILD_TYPE,
    )
}
