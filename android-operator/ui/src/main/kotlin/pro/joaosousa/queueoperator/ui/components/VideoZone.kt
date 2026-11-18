package pro.joaosousa.queueoperator.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import pro.joaosousa.queuedisplay.tv.domain.model.VideoSource
import pro.joaosousa.queuedisplay.tv.domain.model.VideoSourceResolver

/**
 * The queue's video panel: the right-hand three-fifths of the Flutter home page.
 *
 * [VideoSourceResolver] (frozen in `:domain`) decides *what* the queue's
 * `videoUrl` is, so this composable only decides *how* to show it:
 *
 * - [VideoSource.Direct] → Media3/ExoPlayer, the native equivalent of the
 *   Flutter `video_player` controller (D4).
 * - [VideoSource.YouTube] → a WebView running the official iframe embed (D5).
 *   There is no supported native YouTube player: the Android Player API is
 *   deprecated and needs the YouTube app installed, so an iframe is the closest
 *   honest equivalent to `youtube_player_flutter`.
 * - [VideoSource.None] → an empty black panel, exactly like the Flutter widget
 *   when it had nothing to play.
 */
@Composable
fun VideoZone(
    videoUrl: String,
    volume: Double,
    modifier: Modifier = Modifier,
) {
    val source = remember(videoUrl) { VideoSourceResolver.resolve(videoUrl) }

    Box(modifier = modifier.background(Color.Black)) {
        when (source) {
            VideoSource.None -> Unit
            is VideoSource.Direct -> DirectVideoPlayer(url = source.url, volume = volume)
            is VideoSource.YouTube -> YouTubeEmbed(videoId = source.videoId, volume = volume)
        }
    }
}

/**
 * Looping, muted-or-not playback of a direct media URL.
 *
 * The player is created per URL and released in [DisposableEffect]'s dispose:
 * ExoPlayer holds a codec and a surface, and a counter terminal keeps this
 * screen open for a whole shift, so leaking one per queue switch would eventually
 * exhaust MediaCodec instances.
 */
@Composable
private fun DirectVideoPlayer(url: String, volume: Double) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    // Volume can change without a new URL (the portal edits videoVolume live).
    LaunchedEffect(player, volume) {
        player.volume = volume.toFloat().coerceIn(0f, 1f)
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false // it is signage, not a media app
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * YouTube via the embed iframe (D5). `mute` is derived from the queue's volume
 * because the iframe's HTML player exposes no volume API: anything above zero
 * plays with sound, zero plays muted.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbed(videoId: String, volume: Double) {
    val muted = volume <= 0.0
    val html = remember(videoId, muted) { youTubeEmbedHtml(videoId, muted) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false // autoplay, like the TV
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { webView ->
            // A detached WebView keeps its page alive (and its player running);
            // destroying it stops audio when the operator switches queue.
            webView.stopLoading()
            webView.destroy()
        },
    )
}

private fun youTubeEmbedHtml(videoId: String, muted: Boolean): String {
    val mute = if (muted) 1 else 0
    // `loop` only loops when `playlist` repeats the same id.
    return """
        <!DOCTYPE html>
        <html><head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>html,body{margin:0;padding:0;background:#000;height:100%;}
            iframe{border:0;width:100%;height:100%;}</style>
        </head><body>
          <iframe
            src="https://www.youtube.com/embed/$videoId?autoplay=1&mute=$mute&loop=1&playlist=$videoId&controls=0&modestbranding=1&rel=0&playsinline=1"
            allow="autoplay; encrypted-media"
            allowfullscreen></iframe>
        </body></html>
    """.trimIndent()
}