package com.ziaee.frenchreader.llm

import org.junit.Assert.assertSame
import org.junit.Test

class LlamaCppEngineSingletonTest {
    @Test
    fun getInstanceSharesOneEngineAcrossCallers() {
        val first = LlamaCppEngine.getInstance { "/first/model.gguf" }
        val second = LlamaCppEngine.getInstance { "/second/model.gguf" }

        assertSame(first, second)
    }
}
