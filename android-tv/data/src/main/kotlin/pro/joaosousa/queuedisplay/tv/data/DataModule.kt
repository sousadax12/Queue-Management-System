package pro.joaosousa.queuedisplay.tv.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pro.joaosousa.queuedisplay.tv.data.firebase.FirebaseEndpoint
import pro.joaosousa.queuedisplay.tv.data.mapping.ContractIssueReporter
import pro.joaosousa.queuedisplay.tv.data.mapping.LogcatContractIssueReporter
import pro.joaosousa.queuedisplay.tv.data.repository.FirestoreConfigRepository
import pro.joaosousa.queuedisplay.tv.data.repository.FirestoreDeviceRepository
import pro.joaosousa.queuedisplay.tv.domain.repository.ConfigRepository
import pro.joaosousa.queuedisplay.tv.domain.repository.DeviceRepository

/**
 * Binds the Firestore repositories to their domain interfaces. The app provides the
 * [FirebaseEndpoint]. Tests replace this whole module with fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun deviceRepository(impl: FirestoreDeviceRepository): DeviceRepository

    @Binds
    abstract fun configRepository(impl: FirestoreConfigRepository): ConfigRepository

    @Binds
    abstract fun contractIssueReporter(impl: LogcatContractIssueReporter): ContractIssueReporter

    companion object {
        private const val EMULATOR_APP_NAME = "emulator"

        @Provides
        @Singleton
        fun firebaseApp(@ApplicationContext context: Context, endpoint: FirebaseEndpoint): FirebaseApp {
            val default = checkNotNull(FirebaseApp.initializeApp(context)) {
                "Firebase is not configured (app/google-services.json)"
            }
            if (endpoint.emulatorHost == null) return default
            // A separate app under the emulator's project id, so the seeded data is visible and
            // nothing can reach the real project.
            return FirebaseApp.getApps(context).firstOrNull { it.name == EMULATOR_APP_NAME }
                ?: FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder(default.options).setProjectId(endpoint.emulatorProjectId).build(),
                    EMULATOR_APP_NAME,
                )
        }

        @Provides
        @Singleton
        fun firebaseAuth(app: FirebaseApp, endpoint: FirebaseEndpoint): FirebaseAuth =
            FirebaseAuth.getInstance(app).apply {
                endpoint.emulatorHost?.let { useEmulator(it, endpoint.authPort) }
            }

        @Provides
        @Singleton
        fun firestore(app: FirebaseApp, endpoint: FirebaseEndpoint): FirebaseFirestore =
            FirebaseFirestore.getInstance(app).apply {
                endpoint.emulatorHost?.let { useEmulator(it, endpoint.firestorePort) }
            }
    }
}
