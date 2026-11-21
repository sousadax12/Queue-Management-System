package pro.joaosousa.queuedisplay.operator.auth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the operator's auth seam. `FirebaseAuth` itself comes from `:data`'s `DataModule`, set up
 * by [pro.joaosousa.queuedisplay.operator.firebase.OperatorFirebaseModule]'s endpoint.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OperatorAuthModule {

    @Binds
    abstract fun operatorAuth(impl: FirebaseOperatorAuth): OperatorAuth
}
