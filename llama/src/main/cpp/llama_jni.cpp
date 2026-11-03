// JNI bridge between com.maheshreddy.mym.llama.LlamaNative and llama.cpp.
// Keeps the tokens currently in the KV cache so each new prompt only decodes the part that changed
// (the persona prompt and earlier turns are reused), which is what keeps time-to-first-token low.
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "llama_jni", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "llama_jni", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#define LOGE(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#endif

namespace {

std::mutex g_err_mutex;
std::string g_last_error;

void clearLastError() {
    std::lock_guard<std::mutex> lock(g_err_mutex);
    g_last_error.clear();
}

void recordError(const char *text) {
    std::lock_guard<std::mutex> lock(g_err_mutex);
    std::string line(text);
    while (!line.empty() && (line.back() == '\n' || line.back() == ' ')) line.pop_back();
    if (line.empty()) return;
    if (!g_last_error.empty()) g_last_error += " | ";
    g_last_error += line;
    if (g_last_error.size() > 600) g_last_error.erase(0, g_last_error.size() - 600);
}

struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    std::vector<llama_token> cached;  // tokens currently in the KV cache (sequence 0)
    std::atomic<bool> abort{false};
};

Session *S(jlong p) { return reinterpret_cast<Session *>(p); }

std::string fromJString(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    // GetStringUTFChars returns modified UTF-8, which mangles emoji; go through getBytes("UTF-8").
    jclass strClass = env->GetObjectClass(s);
    jmethodID getBytes = env->GetMethodID(strClass, "getBytes", "(Ljava/lang/String;)[B");
    jstring charset = env->NewStringUTF("UTF-8");
    auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(s, getBytes, charset));
    const jsize len = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(len), '\0');
    env->GetByteArrayRegion(bytes, 0, len, reinterpret_cast<jbyte *>(out.data()));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(strClass);
    return out;
}

jbyteArray toBytes(JNIEnv *env, const std::string &s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text, bool addSpecial) {
    int n = -llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), nullptr, 0, addSpecial, true);
    std::vector<llama_token> tokens(static_cast<size_t>(std::max(n, 0)));
    if (n > 0) {
        llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), tokens.data(), n, addSpecial, true);
    }
    return tokens;
}

std::string piece(const llama_vocab *vocab, llama_token t) {
    char buf[256];
    int n = llama_token_to_piece(vocab, t, buf, sizeof(buf), 0, false);
    if (n < 0) {
        std::string big(static_cast<size_t>(-n), '\0');
        n = llama_token_to_piece(vocab, t, big.data(), -n, 0, false);
        return big.substr(0, static_cast<size_t>(std::max(n, 0)));
    }
    return std::string(buf, static_cast<size_t>(n));
}

/** Length of the longest prefix of [s] that ends on a complete UTF-8 character. */
size_t completeUtf8Prefix(const std::string &s) {
    size_t i = s.size();
    size_t back = 0;
    while (i > 0 && back < 4) {
        auto c = static_cast<unsigned char>(s[i - 1]);
        if ((c & 0xC0) != 0x80) {  // lead byte or ASCII
            size_t need = (c < 0x80) ? 1 : (c >> 5) == 0x6 ? 2 : (c >> 4) == 0xE ? 3 : (c >> 3) == 0x1E ? 4 : 1;
            return (back + 1 >= need) ? s.size() : i - 1;
        }
        --i;
        ++back;
    }
    return s.size();
}

bool decodeTokens(Session *s, const llama_token *tokens, int n) {
    const int batch = static_cast<int>(llama_n_batch(s->ctx));
    for (int i = 0; i < n; i += batch) {
        if (s->abort.load()) return false;
        const int len = std::min(batch, n - i);
        llama_batch b = llama_batch_get_one(const_cast<llama_token *>(tokens + i), len);
        if (llama_decode(s->ctx, b) != 0) return false;
    }
    return true;
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_backendInit(JNIEnv *, jobject) {
    llama_log_set([](ggml_log_level level, const char *text, void *) {
        if (level == GGML_LOG_LEVEL_WARN || level == GGML_LOG_LEVEL_ERROR) {
            LOGE("%s", text);
            recordError(text);
        }
    }, nullptr);
    llama_backend_init();
}

JNIEXPORT jlong JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_load(JNIEnv *env, jobject, jstring jpath, jint nCtx, jint threads) {
    clearLastError();
    const std::string path = fromJString(env, jpath);
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    llama_model *model = nullptr;
    try {
        model = llama_model_load_from_file(path.c_str(), mparams);
    } catch (const std::exception &e) {
        recordError(e.what());
    }
    if (model == nullptr) {
        LOGE("failed to load model %s", path.c_str());
        return 0;
    }
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(nCtx);
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;
    cparams.no_perf = true;
    llama_context *ctx = nullptr;
    try {
        ctx = llama_init_from_model(model, cparams);
    } catch (const std::exception &e) {
        recordError(e.what());
    }
    if (ctx == nullptr) {
        LOGE("failed to create context");
        llama_model_free(model);
        return 0;
    }
    auto *s = new Session();
    s->model = model;
    s->ctx = ctx;
    s->vocab = llama_model_get_vocab(model);
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_free(JNIEnv *, jobject, jlong p) {
    Session *s = S(p);
    if (s == nullptr) return;
    llama_free(s->ctx);
    llama_model_free(s->model);
    delete s;
}

JNIEXPORT jint JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_contextSize(JNIEnv *, jobject, jlong p) {
    return static_cast<jint>(llama_n_ctx(S(p)->ctx));
}

JNIEXPORT jint JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_countTokens(JNIEnv *env, jobject, jlong p, jstring jtext) {
    return static_cast<jint>(tokenize(S(p)->vocab, fromJString(env, jtext), false).size());
}

/** The model's built-in Jinja chat template (or null); used to pick the prompt format. */
JNIEXPORT jstring JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_chatTemplate(JNIEnv *env, jobject, jlong p) {
    const char *tmpl = llama_model_chat_template(S(p)->model, nullptr);
    return tmpl ? env->NewStringUTF(tmpl) : nullptr;
}

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_setThreads(JNIEnv *, jobject, jlong p, jint threads) {
    llama_set_n_threads(S(p)->ctx, threads, threads);
}

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_requestAbort(JNIEnv *, jobject, jlong p) {
    if (S(p) != nullptr) S(p)->abort.store(true);
}

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_clearCache(JNIEnv *, jobject, jlong p) {
    Session *s = S(p);
    llama_memory_clear(llama_get_memory(s->ctx), true);
    s->cached.clear();
}

/**
 * Generates a completion for the fully formatted [jprompt]. Each decoded UTF-8 chunk is passed to
 * callback.onToken(byte[]) which returns false to stop. Returns the number of generated tokens, or -1 on error.
 */
JNIEXPORT jint JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_generate(
        JNIEnv *env, jobject, jlong p, jstring jprompt, jint maxTokens,
        jfloat temperature, jfloat topP, jint seed, jobject callback) {
    Session *s = S(p);
    s->abort.store(false);
    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "([B)Z");

    std::vector<llama_token> prompt = tokenize(s->vocab, fromJString(env, jprompt), true);
    const int nCtx = static_cast<int>(llama_n_ctx(s->ctx));
    if (static_cast<int>(prompt.size()) + maxTokens > nCtx) {
        LOGE("prompt (%zu tokens) + max %d does not fit context %d", prompt.size(), maxTokens, nCtx);
        return -2;
    }

    // Reuse the longest common prefix already in the KV cache. Always re-decode at least one token so logits exist.
    size_t common = 0;
    while (common < s->cached.size() && common < prompt.size() && s->cached[common] == prompt[common]) ++common;
    if (common == prompt.size() && common > 0) --common;
    llama_memory_t mem = llama_get_memory(s->ctx);
    if (!llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(common), -1)) {
        llama_memory_clear(mem, true);
        common = 0;
    }
    s->cached.resize(common);
    if (!decodeTokens(s, prompt.data() + common, static_cast<int>(prompt.size() - common))) {
        llama_memory_clear(mem, true);
        s->cached.clear();
        return s->abort.load() ? 0 : -1;
    }
    s->cached = prompt;

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(s->vocab), 64, 1.1f, 0.0f, 0.0f));
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    }

    std::string pending;
    int generated = 0;
    bool keepGoing = true;
    while (generated < maxTokens && keepGoing && !s->abort.load()) {
        llama_token tok = llama_sampler_sample(smpl, s->ctx, -1);
        if (llama_vocab_is_eog(s->vocab, tok)) break;
        ++generated;
        pending += piece(s->vocab, tok);
        const size_t ready = completeUtf8Prefix(pending);
        if (ready > 0) {
            jbyteArray bytes = toBytes(env, pending.substr(0, ready));
            keepGoing = env->CallBooleanMethod(callback, onToken, bytes);
            env->DeleteLocalRef(bytes);
            if (env->ExceptionCheck()) keepGoing = false;
            pending.erase(0, ready);
        }
        if (!keepGoing) break;
        if (llama_decode(s->ctx, llama_batch_get_one(&tok, 1)) != 0) {
            llama_sampler_free(smpl);
            return -1;
        }
        s->cached.push_back(tok);
    }
    llama_sampler_free(smpl);
    env->DeleteLocalRef(cbClass);
    return generated;
}

JNIEXPORT jstring JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_lastError(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(g_err_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_maheshreddy_mym_llama_LlamaNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

} // extern "C"
