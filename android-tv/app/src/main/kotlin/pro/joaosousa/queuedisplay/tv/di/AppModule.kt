package pro.joaosousa.queuedisplay.tv.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pro.joaosousa.queuedisplay.tv.BuildConfig
import pro.joaosousa.queuedisplay.tv.device.AndroidDeviceFacts
import pro.joaosousa.queuedisplay.tv.device.DeviceFacts
import pro.joaosousa.queuedisplay.tv.data.firebase.FirebaseEndpoint
import pro.joaosousa.queuedisplay.tv.domain.BuildInfo
import pro.joaosousa.queuedisplay.tv.domain.display.ObserveDisplay
import pro.joaosousa.queuedisplay.tv.domain.repository.ConfigRepository
import pro.joaosousa.queuedisplay.tv.domain.repository.DeviceRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun deviceFacts(impl: AndroidDeviceFacts): DeviceFacts

    companion object {
        @Provides
        fun provideBuildInfo(): BuildInfo = BuildInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            buildType = BuildConfig.BUILD_TYPE,
        )

        @Provides
        fun provideFirebaseEndpoint(): FirebaseEndpoint =
            FirebaseEndpoint(emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST.ifBlank { null })

        @Provides
        fun provideObserveDisplay(devices: DeviceRepository, config: ConfigRepository): ObserveDisplay =
            ObserveDisplay(devices, config)
    }
}
