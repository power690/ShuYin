#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <vector>
#include <algorithm>
#define LOG_TAG "NativeBlur"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
static inline int clampInt(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
static inline uint8_t clampByte(int v) { return (uint8_t)(v < 0 ? 0 : (v > 255 ? 255 : v)); }
static void boxesForGauss(float sigma, int* sizes) {
    const int n = 3;
    float wIdeal = std::sqrt((12.0f * sigma * sigma / n) + 1.0f);
    int wl = (int)std::floor(wIdeal);
    if (wl % 2 == 0) --wl;
    int wu = wl + 2;
    float mIdeal = (12.0f * sigma * sigma - n * (float)(wl * wl) - 4.0f * n * (float)wl - 3.0f * n) / (-4.0f * (float)wl - 4.0f);
    int m = (int)std::lround(mIdeal);
    if (m < 0) m = 0;
    if (m > n) m = n;
    for (int i = 0; i < n; ++i) sizes[i] = (i < m ? wl : wu);
}
static void boxBlurH(const uint32_t* src, uint32_t* dst, int w, int h, int radius) {
    int maxR = (w - 1) / 2;
    if (radius < 1 || maxR < 1) { memcpy(dst, src, (size_t)w * h * 4); return; }
    if (radius > maxR) radius = maxR;
    const int div = 2 * radius + 1;
    const int halfDiv = div >> 1;
    for (int y = 0; y < h; ++y) {
        const uint32_t* sRow = src + (size_t)y * w;
        uint32_t* dRow = dst + (size_t)y * w;
        int sumR = 0, sumG = 0, sumB = 0, sumA = 0;
        for (int i = -radius; i <= radius; ++i) {
            uint32_t p = sRow[clampInt(i, 0, w - 1)];
            sumR += (int)(p & 0xFF);
            sumG += (int)((p >> 8) & 0xFF);
            sumB += (int)((p >> 16) & 0xFF);
            sumA += (int)((p >> 24) & 0xFF);
        }
        for (int x = 0; x < w; ++x) {
            dRow[x] = (uint32_t)((sumR + halfDiv) / div) |
                      ((uint32_t)((sumG + halfDiv) / div) << 8) |
                      ((uint32_t)((sumB + halfDiv) / div) << 16) |
                      ((uint32_t)((sumA + halfDiv) / div) << 24);
            uint32_t pRem = sRow[clampInt(x - radius, 0, w - 1)];
            uint32_t pAdd = sRow[clampInt(x + radius + 1, 0, w - 1)];
            sumR += (int)(pAdd & 0xFF) - (int)(pRem & 0xFF);
            sumG += (int)((pAdd >> 8) & 0xFF) - (int)((pRem >> 8) & 0xFF);
            sumB += (int)((pAdd >> 16) & 0xFF) - (int)((pRem >> 16) & 0xFF);
            sumA += (int)((pAdd >> 24) & 0xFF) - (int)((pRem >> 24) & 0xFF);
        }
    }
}
static void boxBlurV(const uint32_t* src, uint32_t* dst, int w, int h, int radius) {
    int maxR = (h - 1) / 2;
    if (radius < 1 || maxR < 1) { memcpy(dst, src, (size_t)w * h * 4); return; }
    if (radius > maxR) radius = maxR;
    const int div = 2 * radius + 1;
    const int halfDiv = div >> 1;
    std::vector<int> sumR(w, 0), sumG(w, 0), sumB(w, 0), sumA(w, 0);
    for (int i = -radius; i <= radius; ++i) {
        const uint32_t* row = src + (size_t)clampInt(i, 0, h - 1) * w;
        for (int x = 0; x < w; ++x) {
            uint32_t p = row[x];
            sumR[x] += (int)(p & 0xFF);
            sumG[x] += (int)((p >> 8) & 0xFF);
            sumB[x] += (int)((p >> 16) & 0xFF);
            sumA[x] += (int)((p >> 24) & 0xFF);
        }
    }
    for (int y = 0; y < h; ++y) {
        uint32_t* dRow = dst + (size_t)y * w;
        for (int x = 0; x < w; ++x) {
            dRow[x] = (uint32_t)((sumR[x] + halfDiv) / div) |
                      ((uint32_t)((sumG[x] + halfDiv) / div) << 8) |
                      ((uint32_t)((sumB[x] + halfDiv) / div) << 16) |
                      ((uint32_t)((sumA[x] + halfDiv) / div) << 24);
        }
        const uint32_t* remRow = src + (size_t)clampInt(y - radius, 0, h - 1) * w;
        const uint32_t* addRow = src + (size_t)clampInt(y + radius + 1, 0, h - 1) * w;
        for (int x = 0; x < w; ++x) {
            uint32_t pRem = remRow[x];
            uint32_t pAdd = addRow[x];
            sumR[x] += (int)(pAdd & 0xFF) - (int)(pRem & 0xFF);
            sumG[x] += (int)((pAdd >> 8) & 0xFF) - (int)((pRem >> 8) & 0xFF);
            sumB[x] += (int)((pAdd >> 16) & 0xFF) - (int)((pRem >> 16) & 0xFF);
            sumA[x] += (int)((pAdd >> 24) & 0xFF) - (int)((pRem >> 24) & 0xFF);
        }
    }
}
static void gaussianBlur(uint32_t* pixels, int w, int h, float sigma) {
    if (sigma < 0.1f || w < 1 || h < 1) return;
    int sizes[3];
    boxesForGauss(sigma, sizes);
    std::vector<uint32_t> temp((size_t)w * h);
    for (int pass = 0; pass < 3; ++pass) {
        int r = (sizes[pass] - 1) / 2;
        boxBlurH(pixels, temp.data(), w, h, r);
        boxBlurV(temp.data(), pixels, w, h, r);
    }
}
static void downscaleArea(const uint32_t* src, int sw, int sh, uint32_t* dst, int dw, int dh) {
    for (int dy = 0; dy < dh; ++dy) {
        int y0 = (int)((long long)dy * sh / dh);
        int y1 = (int)((long long)(dy + 1) * sh / dh);
        if (y1 <= y0) y1 = y0 + 1;
        if (y1 > sh) y1 = sh;
        for (int dx = 0; dx < dw; ++dx) {
            int x0 = (int)((long long)dx * sw / dw);
            int x1 = (int)((long long)(dx + 1) * sw / dw);
            if (x1 <= x0) x1 = x0 + 1;
            if (x1 > sw) x1 = sw;
            long long sR = 0, sG = 0, sB = 0, sA = 0, cnt = 0;
            for (int y = y0; y < y1; ++y) {
                const uint32_t* row = src + (size_t)y * sw;
                for (int x = x0; x < x1; ++x) {
                    uint32_t p = row[x];
                    sR += (long long)(p & 0xFF);
                    sG += (long long)((p >> 8) & 0xFF);
                    sB += (long long)((p >> 16) & 0xFF);
                    sA += (long long)((p >> 24) & 0xFF);
                    ++cnt;
                }
            }
            dst[(size_t)dy * dw + dx] = ((uint32_t)(sA / cnt) << 24) |
                                        ((uint32_t)(sB / cnt) << 16) |
                                        ((uint32_t)(sG / cnt) << 8) |
                                        (uint32_t)(sR / cnt);
        }
    }
}
static void upsampleBilinear(const uint32_t* src, int sw, int sh, uint32_t* dst, int dw, int dh) {
    const float sx = (float)sw / (float)dw;
    const float sy = (float)sh / (float)dh;
    for (int dy = 0; dy < dh; ++dy) {
        float fy = (dy + 0.5f) * sy - 0.5f;
        if (fy < 0.0f) fy = 0.0f;
        int y0 = (int)fy;
        if (y0 > sh - 1) y0 = sh - 1;
        int y1 = y0 + 1;
        if (y1 > sh - 1) y1 = sh - 1;
        float ty = fy - (float)y0;
        const uint32_t* r0 = src + (size_t)y0 * sw;
        const uint32_t* r1 = src + (size_t)y1 * sw;
        uint32_t* dRow = dst + (size_t)dy * dw;
        for (int dx = 0; dx < dw; ++dx) {
            float fx = (dx + 0.5f) * sx - 0.5f;
            if (fx < 0.0f) fx = 0.0f;
            int x0 = (int)fx;
            if (x0 > sw - 1) x0 = sw - 1;
            int x1 = x0 + 1;
            if (x1 > sw - 1) x1 = sw - 1;
            float tx = fx - (float)x0;
            uint32_t c00 = r0[x0];
            uint32_t c10 = r0[x1];
            uint32_t c01 = r1[x0];
            uint32_t c11 = r1[x1];
            float w00 = (1.0f - tx) * (1.0f - ty);
            float w10 = tx * (1.0f - ty);
            float w01 = (1.0f - tx) * ty;
            float w11 = tx * ty;
            int r = (int)((float)(c00 & 0xFF) * w00 + (float)(c10 & 0xFF) * w10 + (float)(c01 & 0xFF) * w01 + (float)(c11 & 0xFF) * w11 + 0.5f);
            int g = (int)((float)((c00 >> 8) & 0xFF) * w00 + (float)((c10 >> 8) & 0xFF) * w10 + (float)((c01 >> 8) & 0xFF) * w01 + (float)((c11 >> 8) & 0xFF) * w11 + 0.5f);
            int b = (int)((float)((c00 >> 16) & 0xFF) * w00 + (float)((c10 >> 16) & 0xFF) * w10 + (float)((c01 >> 16) & 0xFF) * w01 + (float)((c11 >> 16) & 0xFF) * w11 + 0.5f);
            int a = (int)((float)((c00 >> 24) & 0xFF) * w00 + (float)((c10 >> 24) & 0xFF) * w10 + (float)((c01 >> 24) & 0xFF) * w01 + (float)((c11 >> 24) & 0xFF) * w11 + 0.5f);
            dRow[dx] = ((uint32_t)clampByte(a) << 24) |
                       ((uint32_t)clampByte(b) << 16) |
                       ((uint32_t)clampByte(g) << 8) |
                       (uint32_t)clampByte(r);
        }
    }
}
static void boostSaturation(uint32_t* pixels, int w, int h, float sat) {
    const size_t n = (size_t)w * h;
    for (size_t i = 0; i < n; ++i) {
        uint32_t p = pixels[i];
        float r = (float)(p & 0xFF);
        float g = (float)((p >> 8) & 0xFF);
        float b = (float)((p >> 16) & 0xFF);
        float gray = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        int nr = (int)(gray + (r - gray) * sat + 0.5f);
        int ng = (int)(gray + (g - gray) * sat + 0.5f);
        int nb = (int)(gray + (b - gray) * sat + 0.5f);
        pixels[i] = (p & 0xFF000000u) |
                    ((uint32_t)clampByte(nb) << 16) |
                    ((uint32_t)clampByte(ng) << 8) |
                    (uint32_t)clampByte(nr);
    }
}
extern "C" JNIEXPORT jobject JNICALL Java_com_xiaowei_player_NativeBlurUtils_nativeBlur(JNIEnv* env, jclass, jobject bitmap, jint radius) {
    if (bitmap == nullptr || radius < 1) return nullptr;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return nullptr;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGD("Only ARGB_8888 supported, got %d", info.format);
        return nullptr;
    }
    int w = (int)info.width;
    int h = (int)info.height;
    if (w < 1 || h < 1) return nullptr;
    void* srcPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &srcPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return nullptr;
    }
    std::vector<uint32_t> src((size_t)w * h);
    if (info.stride == (uint32_t)(w * 4)) {
        memcpy(src.data(), srcPixels, (size_t)w * h * 4);
    } else {
        const uint8_t* base = (const uint8_t*)srcPixels;
        for (int y = 0; y < h; ++y) {
            memcpy(src.data() + (size_t)y * w, base + (size_t)y * info.stride, (size_t)w * 4);
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    int factor = 1;
    if (radius >= 6) {
        factor = radius / 6;
        int cap = std::max(w, h) / 64;
        if (cap < 1) cap = 1;
        if (factor > cap) factor = cap;
        if (factor > 8) factor = 8;
    }
    int sw = w;
    int sh = h;
    std::vector<uint32_t> small;
    uint32_t* blurBuf = src.data();
    if (factor > 1) {
        sw = w / factor;
        if (sw < 1) sw = 1;
        sh = h / factor;
        if (sh < 1) sh = 1;
        small.resize((size_t)sw * sh);
        downscaleArea(src.data(), w, h, small.data(), sw, sh);
        blurBuf = small.data();
    }
    float sigma = (float)radius * 0.7f / (float)factor;
    if (sigma < 1.2f) sigma = 1.2f;
    gaussianBlur(blurBuf, sw, sh, sigma);
    boostSaturation(blurBuf, sw, sh, 1.25f);
    std::vector<uint32_t> out((size_t)w * h);
    if (factor > 1) {
        upsampleBilinear(blurBuf, sw, sh, out.data(), w, h);
    } else {
        memcpy(out.data(), blurBuf, (size_t)w * h * 4);
    }
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmap = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jmethodID valueOf = env->GetStaticMethodID(configClass, "valueOf", "(Ljava/lang/String;)Landroid/graphics/Bitmap$Config;");
    jstring configName = env->NewStringUTF("ARGB_8888");
    jobject config = env->CallStaticObjectMethod(configClass, valueOf, configName);
    env->DeleteLocalRef(configName);
    jobject resultBitmap = env->CallStaticObjectMethod(bitmapClass, createBitmap, w, h, config);
    env->DeleteLocalRef(config);
    if (resultBitmap == nullptr) {
        return nullptr;
    }
    void* dstPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, resultBitmap, &dstPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        env->DeleteLocalRef(resultBitmap);
        return nullptr;
    }
    AndroidBitmapInfo dstInfo;
    bool dstInfoOk = AndroidBitmap_getInfo(env, resultBitmap, &dstInfo) == ANDROID_BITMAP_RESULT_SUCCESS;
    if (!dstInfoOk || dstInfo.stride == (uint32_t)(w * 4)) {
        memcpy(dstPixels, out.data(), (size_t)w * h * 4);
    } else {
        uint8_t* base = (uint8_t*)dstPixels;
        for (int y = 0; y < h; ++y) {
            memcpy(base + (size_t)y * dstInfo.stride, out.data() + (size_t)y * w, (size_t)w * 4);
        }
    }
    AndroidBitmap_unlockPixels(env, resultBitmap);
    return resultBitmap;
}
