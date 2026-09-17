#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>
#include "llama.h"
#include "chat.h"
#include "common.h"
#include "sampling.h"

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

extern "C" JNIEXPORT jstring JNICALL
Java_com_ziaee_frenchreader_llm_LlmNative_nativeGenerate(
        JNIEnv* env, jobject, jlong handlePtr,
        jstring jSystemPrompt, jstring jUserPrompt, jint maxTokens, jstring jGrammar) {
    auto* handle = reinterpret_cast<EngineHandle*>(handlePtr);
    if (!handle || !handle->ctx || !handle->model) return env->NewStringUTF("");

    const char* systemPromptChars = env->GetStringUTFChars(jSystemPrompt, nullptr);
    const char* userPromptChars = env->GetStringUTFChars(jUserPrompt, nullptr);

    common_chat_msg sys_msg; sys_msg.role = "system"; sys_msg.content = systemPromptChars;
    common_chat_msg user_msg; user_msg.role = "user"; user_msg.content = userPromptChars;

    common_chat_templates_inputs inputs;
    inputs.messages = {sys_msg, user_msg};
    inputs.add_generation_prompt = true;
    inputs.enable_thinking = false;  // spike finding: must always be false for Qwen3
    inputs.use_jinja = true;

    env->ReleaseStringUTFChars(jSystemPrompt, systemPromptChars);
    env->ReleaseStringUTFChars(jUserPrompt, userPromptChars);

    common_chat_params chat_params = common_chat_templates_apply(handle->templates.get(), inputs);

    llama_memory_clear(llama_get_memory(handle->ctx), false);

    std::vector<llama_token> tokens = common_tokenize(handle->ctx, chat_params.prompt, true, true);
    if (tokens.empty()) {
        LOGE("nativeGenerate: tokenization produced no tokens");
        return env->NewStringUTF("");
    }

    llama_batch batch = llama_batch_init(512, 0, 1);
    for (size_t i = 0; i < tokens.size(); i += 512) {
        common_batch_clear(batch);
        size_t chunk = std::min((size_t) 512, tokens.size() - i);
        for (size_t j = 0; j < chunk; j++) {
            bool wantLogit = (i + j == tokens.size() - 1);
            common_batch_add(batch, tokens[i + j], (llama_pos)(i + j), {0}, wantLogit);
        }
        if (llama_decode(handle->ctx, batch) != 0) {
            LOGE("nativeGenerate: llama_decode failed during prompt processing");
            llama_batch_free(batch);
            return env->NewStringUTF("");
        }
    }

    common_params_sampling sparams;
    sparams.temp = 0.2f;
    const char* grammarChars = nullptr;
    if (jGrammar != nullptr) {
        grammarChars = env->GetStringUTFChars(jGrammar, nullptr);
        if (grammarChars[0] != '\0') {
            sparams.grammar.type = COMMON_GRAMMAR_TYPE_USER;
            sparams.grammar.grammar = grammarChars;
        }
    }
    common_sampler* sampler = common_sampler_init(handle->model, sparams);
    if (grammarChars) env->ReleaseStringUTFChars(jGrammar, grammarChars);

    std::string result;
    llama_pos pos = (llama_pos) tokens.size();
    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token = common_sampler_sample(sampler, handle->ctx, -1);
        common_sampler_accept(sampler, new_token, true);
        if (llama_vocab_is_eog(llama_model_get_vocab(handle->model), new_token)) break;
        result += common_token_to_piece(handle->ctx, new_token);

        common_batch_clear(batch);
        common_batch_add(batch, new_token, pos, {0}, true);
        pos++;
        if (llama_decode(handle->ctx, batch) != 0) {
            LOGE("nativeGenerate: llama_decode failed during generation");
            break;
        }
    }

    common_sampler_free(sampler);
    llama_batch_free(batch);
    return env->NewStringUTF(result.c_str());
}
