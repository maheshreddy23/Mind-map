// JNI bridge between com.maheshreddy.mym.whisper.WhisperNative and whisper.cpp.
#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <mutex>
#include "whisper.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "whisper_jni", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "whisper_jni", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#define LOGE(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#endif

namespace {

// Last warning/error printed by whisper.cpp/ggml, so Kotlin can show the real reason a load failed.
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

struct Handle {
    whisper_context *ctx = nullptr;
    std::atomic<bool> abort{false};
};

Handle *toHandle(jlong ptr) { return reinterpret_cast<Handle *>(ptr); }

jstring toJString(JNIEnv *env, const std::string &s) {
    // whisper returns UTF-8; NewStringUTF expects modified UTF-8, so go through a byte array to be safe.
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    jclass strClass = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
    jstring charset = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(strClass, ctor, bytes, charset));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    env->DeleteLocalRef(strClass);
    return result;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_initContext(JNIEnv *env, jobject, jstring jpath) {
    clearLastError();
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = nullptr;
    try {
        ctx = whisper_init_from_file_with_params(path, cparams);
    } catch (const std::exception &e) {
        recordError(e.what());
    } catch (...) {
        recordError("unknown C++ exception while loading the model");
    }
    env->ReleaseStringUTFChars(jpath, path);
    if (ctx == nullptr) {
        LOGE("failed to load whisper model");
        return 0;
    }
    auto *h = new Handle();
    h->ctx = ctx;
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_freeContext(JNIEnv *, jobject, jlong ptr) {
    Handle *h = toHandle(ptr);
    if (h == nullptr) return;
    whisper_free(h->ctx);
    delete h;
}

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_requestAbort(JNIEnv *, jobject, jlong ptr) {
    Handle *h = toHandle(ptr);
    if (h != nullptr) h->abort.store(true);
}

JNIEXPORT jstring JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_transcribe(
        JNIEnv *env, jobject, jlong ptr, jfloatArray jsamples, jint threads, jstring jprompt) {
    Handle *h = toHandle(ptr);
    if (h == nullptr) return nullptr;
    h->abort.store(false);

    const jsize n = env->GetArrayLength(jsamples);
    std::vector<float> samples(static_cast<size_t>(n));
    env->GetFloatArrayRegion(jsamples, 0, n, samples.data());

    std::string prompt;
    if (jprompt != nullptr) {
        const char *p = env->GetStringUTFChars(jprompt, nullptr);
        prompt = p;
        env->ReleaseStringUTFChars(jprompt, p);
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads > 0 ? threads : 4;
    params.language = "en";
    params.translate = false;
    params.no_context = true;
    params.single_segment = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    params.initial_prompt = prompt.empty() ? nullptr : prompt.c_str();
    params.abort_callback = [](void *data) -> bool {
        return static_cast<Handle *>(data)->abort.load();
    };
    params.abort_callback_user_data = h;

    if (whisper_full(h->ctx, params, samples.data(), static_cast<int>(samples.size())) != 0) {
        LOGE("whisper_full failed");
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(h->ctx);
    for (int i = 0; i < segments; ++i) {
        text += whisper_full_get_segment_text(h->ctx, i);
    }
    return toJString(env, text);
}

JNIEXPORT jstring JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_lastError(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(g_err_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}

// ---- Silero VAD (streaming) -------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_vadInit(JNIEnv *env, jobject, jstring jpath, jint threads) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    whisper_vad_context_params vparams = whisper_vad_default_context_params();
    vparams.n_threads = threads > 0 ? threads : 1;
    vparams.use_gpu = false;
    clearLastError();
    whisper_vad_context *vctx = nullptr;
    try {
        vctx = whisper_vad_init_from_file_with_params(path, vparams);
    } catch (const std::exception &e) {
        recordError(e.what());
    }
    env->ReleaseStringUTFChars(jpath, path);
    // whisper.cpp allocates the LSTM h/c state without clearing it; only whisper_vad_reset_state zeroes it.
    // The streaming (_no_reset) path would otherwise start from uninitialized memory.
    if (vctx != nullptr) whisper_vad_reset_state(vctx);
    return reinterpret_cast<jlong>(vctx);
}

/** Returns the highest speech probability across the (512-sample) windows in [jsamples]; LSTM state carries over. */
JNIEXPORT jfloat JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_vadProbability(JNIEnv *env, jobject, jlong ptr, jfloatArray jsamples) {
    auto *vctx = reinterpret_cast<whisper_vad_context *>(ptr);
    if (vctx == nullptr) return -1.0f;
    const jsize n = env->GetArrayLength(jsamples);
    std::vector<float> samples(static_cast<size_t>(n));
    env->GetFloatArrayRegion(jsamples, 0, n, samples.data());
    if (!whisper_vad_detect_speech_no_reset(vctx, samples.data(), static_cast<int>(n))) return -1.0f;
    const int count = whisper_vad_n_probs(vctx);
    const float *probs = whisper_vad_probs(vctx);
    float best = 0.0f;
    for (int i = 0; i < count; ++i) best = probs[i] > best ? probs[i] : best;
    return best;
}

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_vadReset(JNIEnv *, jobject, jlong ptr) {
    auto *vctx = reinterpret_cast<whisper_vad_context *>(ptr);
    if (vctx != nullptr) whisper_vad_reset_state(vctx);
}

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_vadFree(JNIEnv *, jobject, jlong ptr) {
    auto *vctx = reinterpret_cast<whisper_vad_context *>(ptr);
    if (vctx != nullptr) whisper_vad_free(vctx);
}

JNIEXPORT void JNICALL
Java_com_maheshreddy_mym_whisper_WhisperNative_installLogger(JNIEnv *, jobject) {
    // whisper/ggml log every VAD chunk at INFO; only forward warnings and errors.
    whisper_log_set([](ggml_log_level level, const char *text, void *) {
        if (level == GGML_LOG_LEVEL_WARN || level == GGML_LOG_LEVEL_ERROR) {
            LOGE("%s", text);
            recordError(text);
        }
    }, nullptr);
}

} // extern "C"
