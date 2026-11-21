package pro.joaosousa.queuedisplay.operator.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import androidx.tv.material3.Surface
import pro.joaosousa.queuedisplay.operator.queues.QueueError
import pro.joaosousa.queuedisplay.operator.queues.QueueListState
import pro.joaosousa.queuedisplay.tv.domain.model.Queue


/**
 * Login, setup and home goldens on the two TV sizes: 1920×1080 (a 1080p box at xhdpi, 960×540 dp) and 1280×720
 * (a 720p box at mdpi, 1280×720 dp). The video slot is left empty (O5). Record with
 * `./gradlew :operator:recordRoborazziDebug`, check with `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-21T09:05:00Z"), ZoneOffset.UTC)

    private val headlines = listOf(
        "Queue display goes native on the TV boxes",
        "Operators advance numbers with the remote, online or offline",
        "Third headline that scrolls past the edge of the screen",
    )

    private fun capture(name: String, queues: List<Queue>, headlines: List<String>) {
        for ((size, qualifiers) in SIZES) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            composeRule.setContent {
                OperatorTheme {
                    HomeScreen(
                        queues = QueueListState.Data(queues),
                        writeError = null,
                        headlines = headlines,
                        tickerVelocity = 60.0,
                        onAdvance = {},
                        onBack = {},
                        clock = clock,
                    )
                }
            }
            // The marquee doesn't advance with a paused clock, so the ticker is at its start.
            composeRule.mainClock.autoAdvance = false
            composeRule.onRoot().captureRoboImage("src/test/screenshots/home_${name}_$size.png")
        }
    }

    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        for ((size, qualifiers) in SIZES) {
            RuntimeEnvironment.setQualifiers(qualifiers)
            composeRule.setContent { OperatorTheme { Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) { content() } } }
            composeRule.mainClock.autoAdvance = false
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${name}_$size.png")
        }
    }

    @Test
    fun login() = captureScreen("login") {
        LoginScreen(signingIn = false, failure = null, onSubmit = { _, _ -> }, onDismissFailure = {})
    }

    @Test
    fun setup() = captureScreen("setup") {
        SetupScreen(queues = QueueListState.Data(listOf(BALCAO, CONSULTAS)), onSelect = {}, onRetry = {}, onSignOut = {})
    }

    @Test
    fun setupEmpty() = captureScreen("setup_empty") {
        SetupScreen(queues = QueueListState.Data(emptyList()), onSelect = {}, onRetry = {}, onSignOut = {})
    }

    @Test
    fun setupAccessDenied() = captureScreen("setup_access_denied") {
        SetupScreen(
            queues = QueueListState.Failed(QueueError.PermissionDenied),
            onSelect = {},
            onRetry = {},
            onSignOut = {},
        )
    }

    @Test
    fun twoQueues() = capture("two_queues", listOf(BALCAO, CONSULTAS), headlines)

    @Test
    fun longQueueName() = capture("long_name", listOf(BALCAO, LONG_NAME), headlines)

    @Test
    fun tickerHidden() = capture("ticker_hidden", listOf(BALCAO, CONSULTAS), emptyList())

    private companion object {
        val SIZES = listOf(
            "1920x1080" to "w960dp-h540dp-land-xhdpi",
            "1280x720" to "w1280dp-h720dp-land-mdpi",
        )

        fun queue(id: String, name: String, number: Int, icon: Int, background: Long) = Queue(
            id = id,
            siteId = "site_a",
            name = name,
            number = number,
            icon = icon,
            backgroundColor = background,
            foregroundColor = 0xffffffffL,
            videoUrl = "",
            videoVolume = 0.0,
            soundVolume = 1.0,
            velocity = 60.0,
        )

        val BALCAO = queue("balcao", "Balcão A", 12, 0xf007, 0xff2d5b9fL) // user
        val CONSULTAS = queue("consultas", "Consultas", 7, 0xf0f1, 0xff1b7f4bL) // stethoscope
        val LONG_NAME = queue("long", "Atendimento Prioritário e Serviços Sociais", 99, 0xf111, 0xffa33a2aL) // circle
    }
}
