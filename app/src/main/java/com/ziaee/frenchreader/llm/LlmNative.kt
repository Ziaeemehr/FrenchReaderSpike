package com.ziaee.frenchreader.llm

internal object LlmNative {
    init {
        System.loadLibrary("llm_jni")
    }

    external fun nativeSanityCheck(): Int
}
