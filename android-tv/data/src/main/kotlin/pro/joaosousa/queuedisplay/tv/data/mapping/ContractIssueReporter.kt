package pro.joaosousa.queuedisplay.tv.data.mapping

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/** Where contract issues go. Logcat for now; the diagnostics overlay (M7) will read them too. */
fun interface ContractIssueReporter {
    fun report(issues: List<ContractIssue>)
}

/**
 * Logs each distinct issue once per process. Listeners re-map a doc on every change (a queue
 * on every number), so without this the same issue would repeat all day.
 */
@Singleton
class LogcatContractIssueReporter @Inject constructor() : ContractIssueReporter {
    private val seen = LinkedHashSet<ContractIssue>()

    override fun report(issues: List<ContractIssue>) {
        val fresh = synchronized(seen) {
            issues.filter { seen.add(it) }.also { trim() }
        }
        fresh.forEach { Log.w(TAG, "Contract: $it") }
    }

    private fun trim() {
        val it = seen.iterator()
        while (seen.size > MAX_REMEMBERED && it.hasNext()) {
            it.next()
            it.remove()
        }
    }

    private companion object {
        const val TAG = "QueueDisplay"
        const val MAX_REMEMBERED = 500
    }
}

/** Reports [Mapped.issues] and returns the value. */
internal fun <T> ContractIssueReporter.unwrap(mapped: Mapped<T>): T? {
    if (mapped.issues.isNotEmpty()) report(mapped.issues)
    return mapped.value
}
