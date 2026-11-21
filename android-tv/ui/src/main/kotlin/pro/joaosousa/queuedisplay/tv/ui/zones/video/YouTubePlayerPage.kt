package pro.joaosousa.queuedisplay.tv.ui.zones.video

/** The name of the JavaScript bridge the page reports errors through. */
internal const val YOUTUBE_BRIDGE = "QmsVideoBridge"

/**
 * The HTML page hosting the YouTube IFrame player for [video]: autoplaying, **muted** and
 * looping, with no controls, as Flutter's `youtube_player_flutter` flags (`mute: true`,
 * `loop: true`, captions on). Any player error, or a failure to load the IFrame API, calls
 * `QmsVideoBridge.onError(code)`.
 *
 * [origin] is the page's base URL (`https://<applicationId>`), which YouTube needs as the
 * embedding origin and referrer. The video id is safe to inline: [classifyLegacyVideoUrl] only
 * lets 11-character `[A-Za-z0-9_-]` ids through.
 */
internal fun youTubePlayerPage(video: LegacyVideo.YouTube, origin: String): String {
    val id = video.videoId
    return """
        <!doctype html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html, body { margin: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
          #player { width: 100%; height: 100%; }
        </style>
        </head>
        <body>
        <div id="player"></div>
        <script>
          function fail(code) { $YOUTUBE_BRIDGE.onError(code); }
          function onYouTubeIframeAPIReady() {
            new YT.Player('player', {
              width: '100%',
              height: '100%',
              videoId: '$id',
              playerVars: {
                autoplay: 1, mute: 1, loop: 1, playlist: '$id', controls: 0, disablekb: 1,
                fs: 0, rel: 0, playsinline: 1, iv_load_policy: 3, cc_load_policy: 1,
                origin: '$origin'
              },
              events: {
                onReady: function (e) { e.target.mute(); e.target.playVideo(); },
                onStateChange: function (e) {
                  if (e.data === YT.PlayerState.ENDED) { e.target.seekTo(0, true); e.target.playVideo(); }
                },
                onError: function (e) { fail(e.data); }
              }
            });
          }
        </script>
        <script src="https://www.youtube.com/iframe_api" onerror="fail(-1)"></script>
        </body>
        </html>
    """.trimIndent()
}
