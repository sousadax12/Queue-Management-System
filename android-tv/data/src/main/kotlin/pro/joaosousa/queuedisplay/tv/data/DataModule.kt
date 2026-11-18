package pro.joaosousa.queuedisplay.tv.data

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pro.joaosousa.queuedisplay.tv.data.firestore.FirebaseFirestoreGateway
import pro.joaosousa.queuedisplay.tv.data.firestore.FirestoreGateway
import pro.joaosousa.queuedisplay.tv.data.repository.AuthRepository
import pro.joaosousa.queuedisplay.tv.data.repository.ConfigRepository
import pro.joaosousa.queuedisplay.tv.data.repository.FirebaseAuthRepository
import pro.joaosousa.queuedisplay.tv.data.repository.FirestoreConfigRepository
import pro.joaosousa.queuedisplay.tv.data.repository.FirestoreQueueRepository
import pro.joaosousa.queuedisplay.tv.data.repository.HttpRssRepository
import pro.joaosousa.queuedisplay.tv.data.repository.QueueRepository
import pro.joaosousa.queuedisplay.tv.data.repository.RssRepository
import javax.inject.Singleton

/**
 * Wires `:data` into Hilt.
 *
 * Firebase is resolved from [FirebaseApp] and never from `BuildConfig` or an application id: the
 * same `:data` module is compiled into the TV app (`pro.joaosousa.queuedisplay.tv`) and the operator
 * app (design D1/D2, a different id in the same Firebase project), so a hard-coded host package
 * would be wrong for one of them. `getInstance()` also keeps the SDK lazy: anything that never
 * opens a repository never touches Firebase.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun authRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    abstract fun queueRepository(impl: FirestoreQueueRepository): QueueRepository

    @Binds
    abstract fun configRepository(impl: FirestoreConfigRepository): ConfigRepository

    @Binds
    abstract fun firestoreGateway(impl: FirebaseFirestoreGateway): FirestoreGateway

    companion object {

        @Provides
        @Singleton
        fun firebaseApp(): FirebaseApp = FirebaseApp.getInstance()

        @Provides
        @Singleton
        fun firestore(app: FirebaseApp): FirebaseFirestore = FirebaseFirestore.getInstance(app)

        @Provides
        @Singleton
        fun auth(app: FirebaseApp): FirebaseAuth = FirebaseAuth.getInstance(app)

        /** Provided rather than bound: its fetch seam is a function, which Dagger cannot supply. */
        @Provides
        @Singleton
        fun rssRepository(): RssRepository = HttpRssRepository()
    }
}