#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include "chat.h"

#define LOG_TAG "LlmJni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

struct EngineHandle {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    common_chat_templates_ptr templates;
};

static bool g_backend_initialized = false;

extern "C" JNIEXPORT jint JNICALL
Java_com_ziaee_frenchreader_llm_LlmNative_nativeSanityCheck(JNIEnv*, jobject) {
    return 42;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ziaee_frenchreader_llm_LlmNative_nativeLoadModel(
        JNIEnv* env, jobject, jstring jModelPath, jint nCtx, jint nThreads) {
    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);
    llama_model_params model_params = llama_model_default_params();
    llama_model* model = llama_model_load_from_file(modelPath, model_params);
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    if (!model) {
        LOGE("nativeLoadModel: llama_model_load_from_file failed");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (uint32_t) nCtx;
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;
    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("nativeLoadModel: llama_init_from_model failed");
        llama_model_free(model);
        return 0;
    }

    auto* handle = new EngineHandle();
    handle->model = model;
    handle->ctx = ctx;
    handle->templates = common_chat_templates_init(model, "");
    LOGI("nativeLoadModel: success, handle=%p", handle);
    return reinterpret_cast<jlong>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ziaee_frenchreader_llm_LlmNative_nativeUnload(JNIEnv*, jobject, jlong handlePtr) {
    auto* handle = reinterpret_cast<EngineHandle*>(handlePtr);
    if (!handle) return;
    handle->templates.reset();
    if (handle->ctx) llama_free(handle->ctx);
    if (handle->model) llama_model_free(handle->model);
    delete handle;
}
