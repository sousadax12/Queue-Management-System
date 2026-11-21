package pro.joaosousa.queuedisplay.tv.domain.operator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pro.joaosousa.queuedisplay.tv.domain.operator.QueueNumber.Direction

class QueueNumberTest {

    @Test
    fun plusOneAndMinusOne() {
        assertEquals(43, QueueNumber.next(42))
        assertEquals(41, QueueNumber.previous(42))
        assertEquals(1, QueueNumber.next(0))
        assertEquals(98, QueueNumber.previous(99))
    }

    @Test
    fun wrapsFrom99To0() {
        assertEquals(0, QueueNumber.next(99))
        assertEquals(0, QueueNumber.step(99, Direction.Up))
    }

    @Test
    fun wrapsFrom0To99() {
        assertEquals(99, QueueNumber.previous(0))
        assertEquals(99, QueueNumber.step(0, Direction.Down))
    }

    @Test
    fun stepFollowsTheDirection() {
        assertEquals(8, QueueNumber.step(7, Direction.Up))
        assertEquals(6, QueueNumber.step(7, Direction.Down))
    }

    @Test
    fun aRoundTripOfAHundredStepsComesBack() {
        var n = 42
        repeat(100) { n = QueueNumber.next(n) }
        assertEquals(42, n)
        repeat(100) { n = QueueNumber.previous(n) }
        assertEquals(42, n)
    }

    @Test
    fun outOfRangeValuesStepIntoTheRange() {
        assertEquals(0, QueueNumber.next(150))
        assertEquals(0, QueueNumber.next(-3))
        assertEquals(99, QueueNumber.previous(150))
        assertEquals(99, QueueNumber.previous(-3))
    }
}
