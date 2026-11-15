package pro.joaosousa.queuedisplay.tv.domain

/** Identifies the running build, shown on screen and later reported in device status. */
data class BuildInfo(
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
) {
    /** Human-readable label, e.g. `0.1.0 (1)` for release or `0.1.0 (1) debug` otherwise. */
    val label: String
        get() = buildString {
            append(versionName).append(" (").append(versionCode).append(')')
            if (buildType != RELEASE) append(' ').append(buildType)
        }

    private companion object {
        const val RELEASE = "release"
    }
}
