package de.haberland.meilists

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns database work from one listener generation. Used on the main dispatcher. */
internal class SyncSession(private val scope: CoroutineScope, private val onError: (Exception) -> Unit) {
    var generation: Long = 0
        private set
    private val jobs = mutableSetOf<Job>()

    fun reset() {
        generation++
        jobs.toList().forEach { it.cancel() }
        jobs.clear()
    }

    fun launch(expectedGeneration: Long, isCurrentUser: () -> Boolean, block: suspend () -> Unit) {
        val job = scope.launch {
            if (expectedGeneration != generation || !isCurrentUser()) return@launch
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error)
            }
        }
        jobs.add(job)
        job.invokeOnCompletion { jobs.remove(job) }
    }
}
