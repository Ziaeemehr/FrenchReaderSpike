package com.ziaee.frenchreader.llm

internal object LlmNative {
    init {
        System.loadLibrary("llm_jni")
    }

    external fun nativeSanityCheck(): Int
    external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int): Long
    external fun nativeUnload(handle: Long)
}
