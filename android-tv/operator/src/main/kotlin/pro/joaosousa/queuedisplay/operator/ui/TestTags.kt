package pro.joaosousa.queuedisplay.operator.ui

/** Test tags for the Robolectric tests. */
object TestTags {
    const val EMAIL = "login_email"
    const val PASSWORD = "login_password"
    const val SUBMIT = "login_submit"
    const val MESSAGE_ACTION = "message_action"
    const val SIGN_OUT = "sign_out"
    const val RETRY = "retry"
    const val SETUP_ROW = "setup_row"
    const val HOME = "home"
    const val QUEUES_EMPTY = "queues_empty"
    const val QUEUES_ERROR = "queues_error"
    const val WRITE_ERROR = "write_error"

    fun setupTile(queueId: String) = "setup_tile_$queueId"
    fun homeTile(queueId: String) = "home_tile_$queueId"
}
