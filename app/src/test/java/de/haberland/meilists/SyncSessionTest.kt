package de.haberland.meilists

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSessionTest {
    @Test fun queuedOldAccountWorkIsDiscarded() = runTest {
        val errors = mutableListOf<Exception>()
        val session = SyncSession(this, errors::add)
        var writes = 0
        session.launch(session.generation, { true }) { writes++ }
        session.reset()
        runCurrent()
        assertEquals(0, writes)
        assertTrue(errors.isEmpty())
    }

    @Test fun removedListenerCannotQueueMoreWork() = runTest {
        val session = SyncSession(this) { fail("Unexpected error") }
        val oldGeneration = session.generation
        session.reset()
        var writes = 0
        session.launch(oldGeneration, { true }) { writes++ }
        session.launch(session.generation, { false }) { writes++ }
        runCurrent()
        assertEquals(0, writes)
        session.launch(session.generation, { true }) { writes++ }
        runCurrent()
        assertEquals(1, writes)
    }

    @Test fun suspendedWorkIsCancelledWithoutShowingAnError() = runTest {
        val errors = mutableListOf<Exception>()
        val session = SyncSession(this, errors::add)
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        session.launch(session.generation, { true }) { gate.await(); writes++ }
        runCurrent()
        session.reset()
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, writes)
        assertTrue(errors.isEmpty())
    }

    @Test fun databaseFailureIsReportedAndNextUpdateStillWorks() = runTest {
        val errors = mutableListOf<Exception>()
        val session = SyncSession(this, errors::add)
        val failure = IllegalStateException("disk full")
        session.launch(session.generation, { true }) { throw failure }
        runCurrent()
        assertEquals(listOf(failure), errors)
        var written = false
        session.launch(session.generation, { true }) { written = true }
        runCurrent()
        assertTrue(written)
    }
}
