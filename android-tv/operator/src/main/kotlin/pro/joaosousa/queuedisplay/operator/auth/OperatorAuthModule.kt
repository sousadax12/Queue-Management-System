package pro.joaosousa.queuedisplay.operator.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pro.joaosousa.queuedisplay.operator.BuildConfig

/**
 * Firebase Auth for the operator. With `-PfirebaseEmulatorHost=<host>` (debug only), Auth goes to
 * the local emulator under the contract seed's project `demo-qms` and never to the real project.
 *
 * The operator app doesn't depend on `:data`, so nothing here clashes with the TV's bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OperatorAuthModule {

    @Binds
    abstract fun operatorAuth(impl: FirebaseOperatorAuth): OperatorAuth

    companion object {
        private const val EMULATOR_APP_NAME = "emulator"
        private const val EMULATOR_PROJECT_ID = "demo-qms"
        private const val EMULATOR_AUTH_PORT = 9099

        @Provides
        @Singleton
        fun firebaseAuth(@ApplicationContext context: Context): FirebaseAuth {
            val default = checkNotNull(FirebaseApp.initializeApp(context)) {
                "Firebase is not configured (operator/google-services.json)"
            }
            val host = BuildConfig.FIREBASE_EMULATOR_HOST.ifBlank { null }
                ?: return FirebaseAuth.getInstance(default)
            val emulatorApp = FirebaseApp.getApps(context).firstOrNull { it.name == EMULATOR_APP_NAME }
                ?: FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder(default.options).setProjectId(EMULATOR_PROJECT_ID).build(),
                    EMULATOR_APP_NAME,
                )
            return FirebaseAuth.getInstance(emulatorApp).apply { useEmulator(host, EMULATOR_AUTH_PORT) }
        }
    }
}
