package com.xiaowei.player.navkit

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

const val STACK_SPRING_STIFFNESS = 300f

const val PROGRESS_VISIBILITY_THRESHOLD = 0.0005f

fun stackSceneSpringSpec() = spring<Float>(
    stiffness = STACK_SPRING_STIFFNESS,
    dampingRatio = Spring.DampingRatioNoBouncy,
    visibilityThreshold = PROGRESS_VISIBILITY_THRESHOLD
)

const val COMPRESS_SCALE_MIN = 0.74f

const val COMPRESS_TRANSLATE_FRACTION = 0.06f

const val BLUR_MAX_DP = 24f

const val ENTER_RADIUS_DP = 28f

const val ENTER_SCALE_MIN = 0.5f

const val ENTER_SHADOW_MAX = 20f
