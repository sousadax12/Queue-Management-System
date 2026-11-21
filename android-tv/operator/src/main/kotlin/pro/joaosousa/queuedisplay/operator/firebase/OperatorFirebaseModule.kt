package pro.joaosousa.queuedisplay.operator.firebase

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pro.joaosousa.queuedisplay.operator.BuildConfig
import pro.joaosousa.queuedisplay.tv.data.firebase.FirebaseEndpoint

/**
 * Which backend the operator talks to. `:data`'s `DataModule` builds `FirebaseApp`, `FirebaseAuth`
 * and `FirebaseFirestore` from it, as it does for the TV app. With `-PfirebaseEmulatorHost=<host>`
 * (debug only), Auth and Firestore go to the local emulators under the contract seed's project
 * `demo-qms` and never to the real project.
 *
 * The operator never rebinds what `DataModule` binds for the TV (design, Option B): its queue
 * repository (`operator/queues`) has a type of its own.
 */
@Module
@InstallIn(SingletonComponent::class)
object OperatorFirebaseModule {

    @Provides
    fun firebaseEndpoint(): FirebaseEndpoint =
        FirebaseEndpoint(emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST.ifBlank { null })
}
