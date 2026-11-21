package pro.joaosousa.queuedisplay.operator.queues

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import pro.joaosousa.queuedisplay.operator.R

/** The sound an operator press makes (design F8). */
fun interface Chime {
    /** Plays the chime at [volume], a 0..1 gain (the queue's `soundVolume`). */
    fun play(volume: Double)
}

/**
 * The Flutter app's `assets/sounds/pixiedust.mp3`, preloaded into a [SoundPool] so a press plays
 * it without decoding. A press before the sound has loaded is silent rather than late.
 */
@Singleton
class SoundPoolChime @Inject constructor(
    @ApplicationContext context: Context,
) : Chime {

    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    @Volatile
    private var loaded = false

    private val soundId: Int = pool.load(context, R.raw.pixiedust, 1)

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (id == soundId && status == 0) loaded = true
        }
    }

    override fun play(volume: Double) {
        val gain = volume.toFloat().coerceIn(0f, 1f)
        if (!loaded || gain == 0f) return
        pool.play(soundId, gain, gain, 1, 0, 1f)
    }

    private companion object {
        // Fast presses overlap instead of cutting each other off.
        const val MAX_STREAMS = 4
    }
}
