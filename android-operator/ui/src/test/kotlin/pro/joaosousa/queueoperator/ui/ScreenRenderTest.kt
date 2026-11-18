package pro.joaosousa.queueoperator.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import pro.joaosousa.queuedisplay.tv.domain.model.FeedItem
import pro.joaosousa.queuedisplay.tv.domain.model.OperatorIdentity
import pro.joaosousa.queuedisplay.tv.domain.model.OperatorRole
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queueoperator.ui.screens.HomeScreen
import pro.joaosousa.queueoperator.ui.screens.LoginScreen
import pro.joaosousa.queueoperator.ui.screens.SetupScreen
import pro.joaosousa.queueoperator.ui.theme.OperatorTheme

/**
 * Renders each screen off-device and writes a PNG, so a UI change can be shown
 * rather than described. Runs on the JVM (Robolectric + native graphics), which
 * is the only render that works without a device or a seeded Firestore.
 *
 * The PNGs land in `ui/build/screenshots/` and are attached to the PR.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val outputDir = File("build/screenshots").apply { mkdirs() }

    @Test
    fun `setup screen`() {
        compose.setContent {
            OperatorTheme {
                SetupScreen(
                    state = OperatorUiState(
                        screen = OperatorScreen.SETUP,
                        sites = listOf(SiteOption("site_porto_3fKq", "Loja Porto Centro")),
                        selectedSiteId = "site_porto_3fKq",
                        screens = listOf(ScreenOption("scr_lobby_left_Q2mz", "Lobby left")),
                        selectedScreenId = "scr_lobby_left_Q2mz",
                        nowMillis = 1_700_000_000_000L,
                    ),
                    onEvent = {},
                )
            }
        }
        capture("01-setup.png")
    }

    @Test
    fun `login screen`() {
        compose.setContent {
            OperatorTheme {
                LoginScreen(
                    state = OperatorUiState(
                        screen = OperatorScreen.LOGIN,
                        selectedSiteId = "site_porto_3fKq",
                        sites = listOf(SiteOption("site_porto_3fKq", "Loja Porto Centro")),
                        email = "balcao@lojas.example",
                        nowMillis = 1_700_000_000_000L,
                    ),
                    onEvent = {},
                )
            }
        }
        capture("02-login.png")
    }

    @Test
    fun `home screen with two queues, a ticker and a staff identity`() {
        compose.setContent {
            OperatorTheme {
                HomeScreen(
                    state = OperatorUiState(
                        screen = OperatorScreen.HOME,
                        sites = listOf(SiteOption("site_porto_3fKq", "Loja Porto Centro")),
                        selectedSiteId = "site_porto_3fKq",
                        identity = OperatorIdentity(
                            uid = "uid_lojas_admin_x81k",
                            email = "balcao@lojas.example",
                            role = OperatorRole.OPERATOR,
                            siteIds = listOf("site_porto_3fKq"),
                        ),
                        queues = listOf(
                            Queue(
                                id = "q_balcao_a_P4tc", orgId = "org_lojas_7Hq2", siteId = "site_porto_3fKq",
                                number = 42L, name = "Balcão A", icon = 61713L,
                                backgroundColor = 4282339765L, foregroundColor = 4294967295L,
                                videoUrl = "", videoVolume = 0.0, soundVolume = 1.0, velocity = 60L,
                            ),
                            Queue(
                                id = "q_balcao_b_Lr6w", orgId = "org_lojas_7Hq2", siteId = "site_porto_3fKq",
                                number = 7L, name = "Balcão B", icon = 61562L,
                                backgroundColor = 4294198070L, foregroundColor = 4294967295L,
                                videoUrl = "", videoVolume = 0.0, soundVolume = 1.0, velocity = 60L,
                            ),
                        ),
                        selectedQueueId = "q_balcao_a_P4tc",
                        ticker = listOf(
                            FeedItem("Andebol: FPA anuncia o calendário dos nacionais", null, null),
                            FeedItem("Seleção prepara o Europeu com dois particulares", null, null),
                        ),
                        nowMillis = 1_700_000_000_000L,
                    ),
                    onEvent = {},
                )
            }
        }
        capture("03-home.png")
    }

    @Test
    fun `home screen degrades instead of blanking when the feed is down`() {
        compose.setContent {
            OperatorTheme {
                HomeScreen(
                    state = OperatorUiState(
                        screen = OperatorScreen.HOME,
                        selectedSiteId = "site_porto_3fKq",
                        queues = listOf(
                            Queue(
                                id = "q_analises_Ce7y", orgId = "org_clinica_K9xP", siteId = "site_lisboa_Wd2e",
                                number = 99L, name = "Análises", icon = 61635L,
                                backgroundColor = 4294940672L, foregroundColor = 4294967295L,
                                videoUrl = "", videoVolume = 0.0, soundVolume = 1.0, velocity = 60L,
                            ),
                        ),
                        selectedQueueId = "q_analises_Ce7y",
                        tickerError = "Não foi possível ler o feed de notícias.",
                        nowMillis = 1_700_000_000_000L,
                    ),
                    onEvent = {},
                )
            }
        }
        capture("04-home-ticker-down.png")
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(outputDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("screenshot written: ${file.absolutePath} (${file.length()} bytes, ${bitmap.width}x${bitmap.height})")
    }
}
