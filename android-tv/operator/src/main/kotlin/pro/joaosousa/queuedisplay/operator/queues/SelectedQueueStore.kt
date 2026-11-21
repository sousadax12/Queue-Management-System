package pro.joaosousa.queuedisplay.operator.queues

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The queue this box operates, remembered across restarts (design Q9). Flutter kept it in memory
 * only, so the operator picked it again after every restart.
 */
interface SelectedQueueStore {
    /** The selected queue id, or null. */
    val selected: Flow<String?>

    suspend fun select(queueId: String)

    suspend fun clear()
}

private val Context.operatorPrefs: DataStore<Preferences> by preferencesDataStore(name = "operator")

/** [SelectedQueueStore] in Preferences DataStore. */
@Singleton
class DataStoreSelectedQueueStore @Inject constructor(
    @ApplicationContext context: Context,
) : SelectedQueueStore {

    private val prefs = context.operatorPrefs

    override val selected: Flow<String?> = prefs.data.map { it[SELECTED_QUEUE] }

    override suspend fun select(queueId: String) {
        prefs.edit { it[SELECTED_QUEUE] = queueId }
    }

    override suspend fun clear() {
        prefs.edit { it.remove(SELECTED_QUEUE) }
    }

    private companion object {
        val SELECTED_QUEUE = stringPreferencesKey("selected_queue_id")
    }
}
