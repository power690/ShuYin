#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <vector>
#include <algorithm>

#define LOG_TAG "NativeBlur"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static inline int clampInt(int v, int lo, int hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

static void boxBlurH(const uint32_t* src, uint32_t* dst,
                     int w, int h, int radius) {
    if (radius < 1) {
        memcpy(dst, src, (size_t)w * h * 4);
        return;
    }

    const int div = 2 * radius + 1;
    const int halfDiv = div >> 1;

    for (int y = 0; y < h; ++y) {
        const uint32_t* sRow = src + (size_t)y * w;
        uint32_t* dRow       = dst + (size_t)y * w;

        int sumR = 0, sumG = 0, sumB = 0;
        for (int i = -radius; i <= radius; ++i) {
            int xi = clampInt(i, 0, w - 1);
            uint32_t p = sRow[xi];
            sumR += (p >> 16) & 0xFF;
            sumG += (p >> 8)  & 0xFF;
            sumB +=  p        & 0xFF;
        }

        for (int x = 0; x < w; ++x) {
            dRow[x] = (0xFFu << 24) |
                      ((uint32_t)((sumR + halfDiv) / div) << 16) |
                      ((uint32_t)((sumG + halfDiv) / div) << 8)  |
                      (uint32_t)((sumB + halfDiv) / div);

            int remIdx = clampInt(x - radius,     0, w - 1);
            int addIdx = clampInt(x + radius + 1, 0, w - 1);

            uint32_t pRem = sRow[remIdx];
            uint32_t pAdd = sRow[addIdx];

            sumR += (int)((pAdd >> 16) & 0xFF) - (int)((pRem >> 16) & 0xFF);
            sumG += (int)((pAdd >> 8)  & 0xFF) - (int)((pRem >> 8)  & 0xFF);
            sumB += (int)( pAdd        & 0xFF) - (int)( pRem        & 0xFF);
        }
    }
}

static void boxBlurV(const uint32_t* src, uint32_t* dst,
                     int w, int h, int radius) {
    if (radius < 1) {
        memcpy(dst, src, (size_t)w * h * 4);
        return;
    }

    const int div = 2 * radius + 1;
    const int halfDiv = div >> 1;

    std::vector<int> sumR(w, 0), sumG(w, 0), sumB(w, 0);

    for (int i = -radius; i <= radius; ++i) {
        int yi = clampInt(i, 0, h - 1);
        const uint32_t* row = src + (size_t)yi * w;
        for (int x = 0; x < w; ++x) {
            uint32_t p = row[x];
            sumR[x] += (p >> 16) & 0xFF;
            sumG[x] += (p >> 8)  & 0xFF;
            sumB[x] +=  p        & 0xFF;
        }
    }

    for (int y = 0; y < h; ++y) {
        uint32_t* dRow = dst + (size_t)y * w;

        for (int x = 0; x < w; ++x) {
            dRow[x] = (0xFFu << 24) |
                      ((uint32_t)((sumR[x] + halfDiv) / div) << 16) |
                      ((uint32_t)((sumG[x] + halfDiv) / div) << 8)  |
                      (uint32_t)((sumB[x] + halfDiv) / div);
        }

        int remIdx = clampInt(y - radius,     0, h - 1);
        int addIdx = clampInt(y + radius + 1, 0, h - 1);
        const uint32_t* remRow = src + (size_t)remIdx * w;
        const uint32_t* addRow = src + (size_t)addIdx * w;

        for (int x = 0; x < w; ++x) {
            uint32_t pRem = remRow[x];
            uint32_t pAdd = addRow[x];
            sumR[x] += (int)((pAdd >> 16) & 0xFF) - (int)((pRem >> 16) & 0xFF);
            sumG[x] += (int)((pAdd >> 8)  & 0xFF) - (int)((pRem >> 8)  & 0xFF);
            sumB[x] += (int)( pAdd        & 0xFF) - (int)( pRem        & 0xFF);
        }
    }
}

static void gaussianBlur(uint32_t* pixels, int w, int h, int radius) {
    if (radius < 1 || w < 1 || h < 1) return;

    int maxRadius = std::max(w, h) / 2;
    radius = std::min(radius, maxRadius);

    std::vector<uint32_t> temp((size_t)w * h);

    for (int pass = 0; pass < 3; ++pass) {
        boxBlurH(pixels, temp.data(), w, h, radius);
        boxBlurV(temp.data(), pixels, w, h, radius);
    }
}

static void adjustBrightnessContrast(uint32_t* pixels, int w, int h,
                                     float brightness, float contrast) {
    const int size = w * h;
    for (int i = 0; i < size; ++i) {
        uint32_t p = pixels[i];
        int r = (p >> 16) & 0xFF;
        int g = (p >> 8)  & 0xFF;
        int b =  p        & 0xFF;

        r = (int)((r - 128) * contrast + 128 + brightness);
        g = (int)((g - 128) * contrast + 128 + brightness);
        b = (int)((b - 128) * contrast + 128 + brightness);

        pixels[i] = (0xFFu << 24) |
                    (clampInt(r, 0, 255) << 16) |
                    (clampInt(g, 0, 255) << 8)  |
                     clampInt(b, 0, 255);
    }
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_xiaowei_player_NativeBlurUtils_nativeBlur(JNIEnv* env, jclass,
                                                    jobject bitmap, jint radius) {
    if (bitmap == nullptr || radius < 1) return nullptr;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return nullptr;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGD("Only ARGB_8888 supported, got %d", info.format);
        return nullptr;
    }

    int w = info.width;
    int h = info.height;

    void* srcPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &srcPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return nullptr;
    }
    uint32_t* workBuffer = new uint32_t[(size_t)w * h];
    memcpy(workBuffer, srcPixels, (size_t)w * h * 4);
    AndroidBitmap_unlockPixels(env, bitmap);

    gaussianBlur(workBuffer, w, h, radius);

    adjustBrightnessContrast(workBuffer, w, h, 8.0f, 1.05f);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmap = env->GetStaticMethodID(bitmapClass, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID valueOf = env->GetStaticMethodID(configClass, "valueOf",
        "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
    jstring configName = env->NewStringUTF("ARGB_8888");
    jobject config = env->CallStaticObjectMethod(configClass, valueOf, configName);
    env->DeleteLocalRef(configName);
    jobject resultBitmap = env->CallStaticObjectMethod(bitmapClass, createBitmap, w, h, config);
    env->DeleteLocalRef(config);

    if (resultBitmap == nullptr) {
        delete[] workBuffer;
        return nullptr;
    }

    void* dstPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, resultBitmap, &dstPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        delete[] workBuffer;
        env->DeleteLocalRef(resultBitmap);
        return nullptr;
    }
    memcpy(dstPixels, workBuffer, (size_t)w * h * 4);
    AndroidBitmap_unlockPixels(env, resultBitmap);

    delete[] workBuffer;
    return resultBitmap;
}
