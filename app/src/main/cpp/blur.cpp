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

// ==========================================================================
// 水平方向 Box Blur —— O(n) 滑动窗口，边缘像素扩展 (clamp to edge)
// 关键修复：src 和 dst 分离，不再原地读写
// ==========================================================================
static void boxBlurH(const uint32_t* src, uint32_t* dst,
                     int w, int h, int radius) {
    if (radius < 1) {
        memcpy(dst, src, (size_t)w * h * 4);
        return;
    }

    const int div = 2 * radius + 1;   // 恒定除数，不再动态修改
    const int halfDiv = div >> 1;     // 用于四舍五入，减少精度累积误差

    for (int y = 0; y < h; ++y) {
        const uint32_t* sRow = src + (size_t)y * w;
        uint32_t* dRow       = dst + (size_t)y * w;

        // --- 初始化 x=0 的窗口累加和 ---
        int sumR = 0, sumG = 0, sumB = 0;
        for (int i = -radius; i <= radius; ++i) {
            int xi = clampInt(i, 0, w - 1);  // 边缘扩展
            uint32_t p = sRow[xi];
            sumR += (p >> 16) & 0xFF;
            sumG += (p >> 8)  & 0xFF;
            sumB +=  p        & 0xFF;
        }

        for (int x = 0; x < w; ++x) {
            // 写入结果（带四舍五入）
            dRow[x] = (0xFFu << 24) |
                      ((uint32_t)((sumR + halfDiv) / div) << 16) |
                      ((uint32_t)((sumG + halfDiv) / div) << 8)  |
                      (uint32_t)((sumB + halfDiv) / div);

            // 滑动窗口：移除最左像素，加入最右像素
            int remIdx = clampInt(x - radius,     0, w - 1);
            int addIdx = clampInt(x + radius + 1, 0, w - 1);

            uint32_t pRem = sRow[remIdx];   // 从 src 读取，不是 dst！
            uint32_t pAdd = sRow[addIdx];

            sumR += (int)((pAdd >> 16) & 0xFF) - (int)((pRem >> 16) & 0xFF);
            sumG += (int)((pAdd >> 8)  & 0xFF) - (int)((pRem >> 8)  & 0xFF);
            sumB += (int)( pAdd        & 0xFF) - (int)( pRem        & 0xFF);
        }
    }
}

// ==========================================================================
// 垂直方向 Box Blur —— O(n)，按行处理，缓存友好
// 使用 per-column 累加和数组，避免逐列访问导致的 cache miss
// ==========================================================================
static void boxBlurV(const uint32_t* src, uint32_t* dst,
                     int w, int h, int radius) {
    if (radius < 1) {
        memcpy(dst, src, (size_t)w * h * 4);
        return;
    }

    const int div = 2 * radius + 1;
    const int halfDiv = div >> 1;

    // 每列一个累加和（行优先访问，缓存友好）
    std::vector<int> sumR(w, 0), sumG(w, 0), sumB(w, 0);

    // --- 初始化 y=0 的窗口，逐行累加 ---
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

        // 写入整行结果
        for (int x = 0; x < w; ++x) {
            dRow[x] = (0xFFu << 24) |
                      ((uint32_t)((sumR[x] + halfDiv) / div) << 16) |
                      ((uint32_t)((sumG[x] + halfDiv) / div) << 8)  |
                      (uint32_t)((sumB[x] + halfDiv) / div);
        }

        // 滑动窗口：移除离开的行，加入进入的行
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

// ==========================================================================
// 三次 Box Blur —— 逼近高斯模糊
// 根据 Central Limit Theorem，多次 box 卷积收敛于高斯分布
// 3 次 box blur 的视觉效果非常接近真实高斯，且复杂度仍为 O(n)
// ==========================================================================
static void gaussianBlur(uint32_t* pixels, int w, int h, int radius) {
    if (radius < 1 || w < 1 || h < 1) return;

    // 限制 radius 不超过图像半尺寸，避免无意义计算
    int maxRadius = std::max(w, h) / 2;
    radius = std::min(radius, maxRadius);

    std::vector<uint32_t> temp((size_t)w * h);

    // 三次迭代：每次 H + V，共 6 趟
    for (int pass = 0; pass < 3; ++pass) {
        boxBlurH(pixels, temp.data(), w, h, radius);
        boxBlurV(temp.data(), pixels, w, h, radius);
    }
}

// ==========================================================================
// 亮度与对比度微调
// ==========================================================================
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

// ==========================================================================
// JNI 入口
// ==========================================================================
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

    // ---- 锁定并拷贝源像素 ----
    void* srcPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &srcPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return nullptr;
    }
    uint32_t* workBuffer = new uint32_t[(size_t)w * h];
    memcpy(workBuffer, srcPixels, (size_t)w * h * 4);
    AndroidBitmap_unlockPixels(env, bitmap);

    // ---- 高质量高斯模糊（三次 Box Blur）----
    gaussianBlur(workBuffer, w, h, radius);

    // ---- 亮度对比度微调 ----
    adjustBrightnessContrast(workBuffer, w, h, 8.0f, 1.05f);

    // ---- 创建目标 Bitmap ----
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

    // ---- 拷贝结果到目标 Bitmap ----
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
