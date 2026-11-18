package pro.joaosousa.queueoperator

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pro.joaosousa.queuedisplay.tv.data.repository.AuthRepository
import pro.joaosousa.queuedisplay.tv.data.repository.AuthState
import pro.joaosousa.queuedisplay.tv.data.repository.ConfigRepository
import pro.joaosousa.queuedisplay.tv.data.repository.QueueRepository
import pro.joaosousa.queuedisplay.tv.data.repository.RssRepository
import pro.joaosousa.queuedisplay.tv.data.repository.ScreenResult
import pro.joaosousa.queuedisplay.tv.domain.model.Feed
import pro.joaosousa.queuedisplay.tv.domain.model.FeedItem
import pro.joaosousa.queuedisplay.tv.domain.model.OperatorIdentity
import pro.joaosousa.queuedisplay.tv.domain.model.OperatorRole
import pro.joaosousa.queuedisplay.tv.domain.model.Queue
import pro.joaosousa.queuedisplay.tv.domain.model.Screen
import pro.joaosousa.queuedisplay.tv.domain.model.ScreenZones
import pro.joaosousa.queuedisplay.tv.domain.model.Site
import pro.joaosousa.queuedisplay.tv.domain.model.TemplateId

/** In-memory stand-ins for the four frozen repositories: no Firebase, no Android. */
class FakeAuthRepository : AuthRepository {

    override val authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    var anonymousSignIns = 0
    var signInResult: Result<OperatorIdentity> = Result.success(STAFF)
    var signOuts = 0

    override suspend fun signInAnonymously() {
        anonymousSignIns++
        authState.value = AuthState.Anonymous("anon-uid")
    }

    override suspend fun signIn(email: String, password: String): Result<OperatorIdentity> = signInResult

    override suspend fun signOut() {
        signOuts++
        authState.value = AuthState.Unknown
    }

    companion object {
        val STAFF = OperatorIdentity(
            uid = "uid_lojas_admin_x81k",
            email = "admin@lojas.example",
            role = OperatorRole.OPERATOR,
            siteIds = listOf(FakeConfigRepository.SITE_ID),
        )
    }
}

class FakeConfigRepository(
    private val sites: List<Site> = listOf(site(SITE_ID, "Loja Porto Centro", SCREEN_ID)),
    private val screens: Map<Pair<String, String>, ScreenResult> = mapOf(
        (SITE_ID to SCREEN_ID) to ScreenResult(screen(SCREEN_ID, "Lobby left"), null),
    ),
) : ConfigRepository {

    override fun sites(): Flow<List<Site>> = flowOf(sites)

    override fun site(siteId: String): Flow<Site?> = flowOf(sites.firstOrNull { it.id == siteId })

    override fun screen(siteId: String, screenId: String): Flow<ScreenResult?> =
        flowOf(screens[siteId to screenId])

    companion object {
        const val SITE_ID = "site_porto_3fKq"
        const val SCREEN_ID = "scr_lobby_left_Q2mz"

        fun site(id: String, name: String, defaultScreenId: String? = null, feedUrl: String? = null) = Site(
            id = id,
            name = name,
            timeZone = "Europe/Lisbon",
            locale = "pt-PT",
            defaultScreenId = defaultScreenId,
            ticker = feedUrl?.let {
                pro.joaosousa.queuedisplay.tv.domain.model.Ticker(
                    feedUrls = listOf(it),
                    speedDpPerSec = 60.0,
                    refreshMinutes = 15L,
                )
            },
            chime = null,
            maintenanceHour = 4L,
        )

        fun screen(id: String, name: String) = Screen(
            id = id,
            name = name,
            template = TemplateId.QUEUES_MEDIA,
            zones = ScreenZones(queues = null, media = null, ticker = null, clock = null),
            maxVideoHeight = 1080L,
            safeAreaPercent = 0L,
        )
    }
}

class FakeQueueRepository(initial: List<Queue> = listOf(queue("q_balcao_a_P4tc", 42L))) : QueueRepository {

    val written = mutableMapOf<String, Long>()
    var failWrites = false
    val live = MutableStateFlow(initial)

    override fun queues(siteId: String): Flow<List<Queue>> = live

    override fun queue(queueId: String): Flow<Queue?> = live.map { list -> list.firstOrNull { it.id == queueId } }

    override suspend fun setNumber(queueId: String, number: Long): Result<Unit> {
        if (failWrites) return Result.failure(IllegalStateException("PERMISSION_DENIED"))
        written[queueId] = number
        live.value = live.value.map { if (it.id == queueId) it.copy(number = number) else it }
        return Result.success(Unit)
    }

    companion object {
        fun queue(id: String, number: Long, name: String = "Balcão A") = Queue(
            id = id,
            orgId = "org_lojas_7Hq2",
            siteId = FakeConfigRepository.SITE_ID,
            number = number,
            name = name,
            icon = 61713L,
            backgroundColor = 4282339765L,
            foregroundColor = 4294967295L,
            videoUrl = "",
            videoVolume = 0.0,
            soundVolume = 1.0,
            velocity = 60L,
        )
    }
}

class FakeRssRepository(
    var result: Result<Feed> = Result.success(Feed("RTP", listOf(FeedItem("Título", null, null)))),
) : RssRepository {

    override suspend fun feed(url: String): Result<Feed> = result

    override fun feedPolled(url: String, refreshMinutes: Long): Flow<Result<Feed>> = flowOf(result)
}
