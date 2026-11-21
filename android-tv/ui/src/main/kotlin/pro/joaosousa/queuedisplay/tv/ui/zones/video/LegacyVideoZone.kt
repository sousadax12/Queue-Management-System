package pro.joaosousa.queuedisplay.tv.ui.zones.video

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame

object LegacyVideoZoneTags {
    const val ZONE = "legacyVideoZone"
    const val DIRECT = "legacyVideoZone.direct"
    const val YOUTUBE = "legacyVideoZone.youtube"
}

/**
 * The legacy video area (design F9): the selected queue's `videoURL`, looping, on black.
 *
 * - Direct URLs play in Media3 ExoPlayer at [videoVolume] (0..1; the portal writes 0 or 1).
 * - YouTube links play in the IFrame player in a WebView, always muted, as Flutter did.
 * - Empty, rejected (playlist-only, malformed) and failing URLs leave the area black. Errors are
 *   kept inside the zone, so the rest of the screen carries on.
 *
 * Pass the **live** queue values: a new [videoUrl] tears the old player down and starts the new
 * one, and a new [videoVolume] applies in place. Players are released when the zone leaves the
 * composition and whenever the lifecycle drops below STARTED; they start again on the way back.
 */
@Composable
fun LegacyVideoZone(videoUrl: String, videoVolume: Double, modifier: Modifier = Modifier) {
    val video = remember(videoUrl) { classifyLegacyVideoUrl(videoUrl) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    Box(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds()
            .testTag(LegacyVideoZoneTags.ZONE),
        contentAlignment = Alignment.Center,
    ) {
        if (lifecycle.isAtLeast(Lifecycle.State.STARTED)) {
            // A new URL is a new player: nothing (player, error) carries over from the old one.
            key(video) {
                when (video) {
                    is LegacyVideo.Direct -> DirectVideo(video, videoVolume)
                    is LegacyVideo.YouTube -> YouTubeVideo(video)
                    LegacyVideo.None, is LegacyVideo.Rejected -> Unit
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun DirectVideo(video: LegacyVideo.Direct, volume: Double) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(video) {
        val exo = ExoPlayer.Builder(context).build()
        exo.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            },
        )
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.setMediaItem(MediaItem.fromUri(video.url))
        exo.playWhenReady = true
        exo.prepare()
        player = exo
        onDispose {
            player = null
            exo.release()
        }
    }
    val current = player ?: return
    SideEffect { current.volume = volume.toFloat().coerceIn(0f, 1f) }
    if (!failed) {
        ContentFrame(
            player = current,
            modifier = Modifier.fillMaxSize().testTag(LegacyVideoZoneTags.DIRECT),
        )
    }
}

@Composable
private fun YouTubeVideo(video: LegacyVideo.YouTube) {
    var failed by remember { mutableStateOf(false) }
    if (failed) return
    AndroidView(
        factory = { context ->
            try {
                youTubeWebView(context, video) { failed = true }
            } catch (e: RuntimeException) {
                // No WebView provider on this box (it throws on construction): stay black.
                failed = true
                View(context)
            }
        },
        onRelease = { view -> (view as? WebView)?.releaseQuietly() },
        modifier = Modifier.fillMaxSize().testTag(LegacyVideoZoneTags.YOUTUBE),
    )
}

@SuppressLint("SetJavaScriptEnabled") // The IFrame player needs it; the page is our own.
private fun youTubeWebView(
    context: android.content.Context,
    video: LegacyVideo.YouTube,
    onError: () -> Unit,
): WebView = WebView(context).apply {
    // The home screen reads the D-pad (queue advancing); the video must never take focus.
    isFocusable = false
    isFocusableInTouchMode = false
    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    setBackgroundColor(android.graphics.Color.BLACK)
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = false
        allowContentAccess = false
    }
    // Called on a WebView thread: hop to the main thread before touching Compose state.
    addJavascriptInterface(
        object {
            @JavascriptInterface
            fun onError(@Suppress("UNUSED_PARAMETER") code: Int) {
                post(onError)
            }
        },
        YOUTUBE_BRIDGE,
    )
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) onError()
        }

        // Without this, a crashed or killed renderer takes the whole app down.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            onError()
            return true
        }
    }
    webChromeClient = object : WebChromeClient() {
        // Hides the grey play-button poster WebView shows before the video starts.
        @SuppressLint("UseKtx") // :ui does not depend on core-ktx for one call.
        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
    val origin = "https://${context.packageName}"
    loadDataWithBaseURL(origin, youTubePlayerPage(video, origin), "text/html", "utf-8", null)
}

private fun WebView.releaseQuietly() {
    stopLoading()
    removeJavascriptInterface(YOUTUBE_BRIDGE)
    (parent as? ViewGroup)?.removeView(this)
    destroy()
}
