package pro.joaosousa.queuedisplay.tv.device

import android.content.Context
import android.os.Build
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import pro.joaosousa.queuedisplay.tv.domain.BuildInfo
import pro.joaosousa.queuedisplay.tv.domain.model.DeviceInfo

/** What the box reports about itself in `devices/{uid}.info` and `.status`. */
interface DeviceFacts {
    fun info(): DeviceInfo

    fun freeBytes(): Long
}

class AndroidDeviceFacts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buildInfo: BuildInfo,
) : DeviceFacts {

    override fun info(): DeviceInfo {
        val metrics = context.resources.displayMetrics
        val (w, h) = maxOf(metrics.widthPixels, metrics.heightPixels) to minOf(metrics.widthPixels, metrics.heightPixels)
        return DeviceInfo(
            model = Build.MODEL.orEmpty().ifBlank { "unknown" },
            sdkInt = Build.VERSION.SDK_INT,
            appVersion = "${buildInfo.versionName} (${buildInfo.versionCode})",
            appVersionCode = buildInfo.versionCode,
            // Decoder detection comes with the media pipeline (M5).
            decoders = emptyList(),
            maxDisplay = "${w}x$h",
        )
    }

    override fun freeBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes
}
