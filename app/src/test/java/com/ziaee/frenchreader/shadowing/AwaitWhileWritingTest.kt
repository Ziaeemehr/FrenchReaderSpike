package com.ziaee.frenchreader.shadowing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AwaitWhileWritingTest {
    @Test fun resultArrivesWhileWriterIsStuck_returnsAndCleansUp() = runTest {
        val result = CompletableDeferred<String>()
        var cleaned = false
        result.complete("le chat")
        val text = awaitResultWhileWriting(
            write = { awaitCancellation() }, // recognizer stopped reading: write never finishes
            result = result,
            onDone = { cleaned = true },
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        assertEquals("le chat", text)
        assertTrue(cleaned)
    }

    @Test fun writerFailsWithBrokenPipe_resultStillReturned() = runTest {
        val result = CompletableDeferred<String>()
        val text = awaitResultWhileWriting(
            write = { result.complete("bonjour"); throw IOException("EPIPE") },
            result = result,
            onDone = {},
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        assertEquals("bonjour", text)
    }
}
