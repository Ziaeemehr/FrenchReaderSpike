#include <jni.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_ziaee_frenchreader_llm_LlmNative_nativeSanityCheck(JNIEnv*, jobject) {
    return 42;
}
