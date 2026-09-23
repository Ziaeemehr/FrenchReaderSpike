package com.ziaee.frenchreader.shadowing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedModelTest {
    @Test fun loadsOnceAcrossSessions() = runTest {
        var loads = 0
        val shared = SharedModel(load = { loads++; "model" }, close = {})
        assertEquals("model", shared.withModel { it })
        assertEquals("model", shared.withModel { it })
        assertEquals(1, loads)
    }

    @Test fun concurrentUsersLoadOnce() = runTest {
        var loads = 0
        val shared = SharedModel(load = { loads++; delay(100); "model" }, close = {})
        listOf(async { shared.withModel { it } }, async { shared.withModel { it } }).awaitAll()
        assertEquals(1, loads)
    }

    @Test fun invalidateClosesAndNextUseReloads() = runTest {
        var n = 0
        val closed = mutableListOf<String>()
        val shared = SharedModel(load = { "m${n++}" }, close = { closed += it })
        assertEquals("m0", shared.withModel { it })
        shared.invalidate()
        assertEquals(listOf("m0"), closed)
        assertEquals("m1", shared.withModel { it })
    }

    @Test fun invalidateWaitsForRunningRecognition() = runTest {
        val closed = mutableListOf<String>()
        val shared = SharedModel(load = { "m" }, close = { closed += it })
        val gate = CompletableDeferred<Unit>()
        val user = launch { shared.withModel { gate.await() } }
        delay(10)
        val invalidator = launch { shared.invalidate() }
        delay(10)
        assertTrue(closed.isEmpty()) // must not free the model while it is in use
        gate.complete(Unit)
        user.join(); invalidator.join()
        assertEquals(listOf("m"), closed)
    }
}
