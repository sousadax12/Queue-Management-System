package pro.joaosousa.queuedisplay.tv.data.mapping

/** Firestore paths the TV reads and writes (contract `src/collections.ts`). */
object Collections {
    const val DEVICES = "devices"
    const val SITES = "sites"
    const val SCREENS = "screens"
    const val PLAYLISTS = "playlists"
    const val QUEUES = "queues"

    fun device(uid: String) = "$DEVICES/$uid"
    fun site(siteId: String) = "$SITES/$siteId"
    fun screen(siteId: String, screenId: String) = "$SITES/$siteId/$SCREENS/$screenId"
    fun playlist(playlistId: String) = "$PLAYLISTS/$playlistId"
    fun queue(queueId: String) = "$QUEUES/$queueId"
}
