package pro.joaosousa.queuedisplay.tv.data.firebase

/**
 * Which backend the app talks to. With an [emulatorHost], Auth and Firestore go to the local
 * emulators under [emulatorProjectId] (the contract seed's `demo-qms`), never to the real project.
 */
data class FirebaseEndpoint(
    /** Host of the Firebase emulators, e.g. `10.0.2.2` from an Android emulator. Null = real project. */
    val emulatorHost: String?,
    val emulatorProjectId: String = DEMO_PROJECT_ID,
    val firestorePort: Int = FIRESTORE_PORT,
    val authPort: Int = AUTH_PORT,
) {
    companion object {
        const val DEMO_PROJECT_ID = "demo-qms"
        const val FIRESTORE_PORT = 8080
        const val AUTH_PORT = 9099
    }
}
