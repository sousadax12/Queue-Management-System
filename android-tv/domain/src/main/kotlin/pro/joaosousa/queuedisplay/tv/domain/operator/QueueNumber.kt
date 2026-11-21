package pro.joaosousa.queuedisplay.tv.domain.operator

/**
 * The number a queue shows, 0..99 (`validQueueNumber` in `firestore.rules`). The operator steps
 * it with the remote and it wraps at both ends, as in the Flutter app
 * (`lib/providers/queues_provider.dart`): 99 → 0 going up, 0 → 99 going down.
 *
 * A value outside 0..99 (only a hand-edited doc could hold one) steps to the nearest end's wrap
 * target, so the result is always one the rules accept.
 */
object QueueNumber {
    const val MIN = 0
    const val MAX = 99

    enum class Direction { Up, Down }

    fun next(number: Int): Int = if (number >= MAX || number < MIN) MIN else number + 1

    fun previous(number: Int): Int = if (number <= MIN || number > MAX) MAX else number - 1

    fun step(number: Int, direction: Direction): Int = when (direction) {
        Direction.Up -> next(number)
        Direction.Down -> previous(number)
    }
}
