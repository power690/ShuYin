package com.xiaowei.player.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.ui.glass.DampedDragAnimation
import com.xiaowei.player.ui.glass.InteractiveHighlight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal val LiquidGlassEnabled: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
internal val LiquidGlassMidEffects: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
internal val LiquidGlassFullEffects: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal val LocalLiquidGlassTabScale =
    staticCompositionLocalOf { { 1f } }

@Composable
fun LiquidGlassNavBar(
    backdrop: Backdrop,
    tabs: List<Pair<ImageVector, String>>,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    forceFrosted: Boolean = false
) {
    val barShape = RoundedCornerShape(30.dp)
    val pillShape = RoundedCornerShape(26.dp)
    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() >= 0.5f
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val fullEffects = !forceFrosted && LiquidGlassFullEffects
    val midEffects = forceFrosted || LiquidGlassMidEffects
    val selectedContentColor = contentColor
    val containerColor =
        if (LiquidGlassMidEffects) {
            if (isLightTheme) Color(0xFFFAFAFA).copy(0.55f)
            else Color(0xFF121212).copy(0.60f)
        } else {
            MaterialTheme.colorScheme.surface
        }

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier.padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabs.size
        }
        val maxStretchPx = with(density) { 22.dp.toPx() }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val stretchOffset = remember {
            Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
        }
        var virtualStretch by remember { mutableFloatStateOf(0f) }
        var virtualStretchY by remember { mutableFloatStateOf(0f) }
        val stretchScope = rememberCoroutineScope()
        val stretchSpring = spring(
            dampingRatio = 0.3f,
            stiffness = 380f,
            visibilityThreshold = Offset.VisibilityThreshold
        )
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabs.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 66f / 54f,
                onDragStarted = {},
                onDragStopped = {
                    virtualStretch = 0f
                    virtualStretchY = 0f
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabs.size - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(0.55f, 480f, 0.5f)
                        )
                    }
                    stretchScope.launch {
                        stretchOffset.animateTo(Offset.Zero, stretchSpring)
                    }
                },
                onDrag = { _, dragAmount ->
                    val delta = dragAmount.x / tabWidth * if (isLtr) 1f else -1f
                    val virtual = targetValue + virtualStretch + delta
                    val clamped = virtual.fastCoerceIn(0f, (tabs.size - 1).toFloat())
                    val overflowPx = (virtual - clamped) * tabWidth
                    val cappedPx = maxStretchPx * overflowPx / (maxStretchPx + abs(overflowPx))
                    virtualStretch = cappedPx / tabWidth
                    updateValue(clamped)
                    virtualStretchY += dragAmount.y
                    val cappedY = maxStretchPx * virtualStretchY / (maxStretchPx + abs(virtualStretchY))
                    val stretchPx = cappedPx * (if (isLtr) 1f else -1f)
                    stretchScope.launch {
                        stretchOffset.snapTo(Offset(stretchPx, cappedY))
                    }
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(Unit) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    if (index != currentIndex) {
                        currentIndex = index
                    }
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    onTabSelected(index)
                    dampedDragAnimation.animateToValue(index.toFloat())
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    val spill = stretchOffset.value.x * 0.5f
                    val spillY = stretchOffset.value.y * 0.5f
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset + spill
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset + spill,
                        size.height / 2f + spillY
                    )
                }
            )
        }

        val tabsContent: @Composable RowScope.() -> Unit = {
            tabs.forEachIndexed { index, tab ->
                val selected = index == currentIndex
                LiquidGlassNavBarTab(
                    onClick = { currentIndex = index },
                    icon = tab.first,
                    label = Strings.get(tab.second),
                    selected = selected,
                    contentColor = contentColor,
                    selectedColor = selectedContentColor
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .graphicsLayer {
                    val ox = stretchOffset.value.x
                    val oy = stretchOffset.value.y
                    val growX = (abs(ox) / size.width).coerceIn(0f, 0.04f)
                    val growY = (abs(oy) / (size.height * 1.1f)).coerceIn(0f, 0.32f)
                    translationX = ox * 0.25f
                    translationY = oy * 0.32f
                    scaleX = (1f + growX) * (1f - growY * 0.45f)
                    scaleY = (1f - growX * 2f) * (1f + growY)
                    val horizontalDominant = abs(ox) > abs(oy)
                    transformOrigin = TransformOrigin(
                        when {
                            horizontalDominant && ox >= 0f -> 0f
                            horizontalDominant -> 1f
                            else -> 0.5f
                        },
                        when {
                            !horizontalDominant && oy >= 0f -> 0f
                            !horizontalDominant -> 1f
                            else -> 0.5f
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { barShape },
                    effects = {
                        vibrancy()
                        blur(if (fullEffects) 8.dp.toPx() else 14.dp.toPx())
                        if (fullEffects) {
                            lens(24.dp.toPx(), 24.dp.toPx())
                        }
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 6.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(62.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = tabsContent
        )

        CompositionLocalProvider(
            LocalLiquidGlassTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { barShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(if (fullEffects) 8.dp.toPx() else 14.dp.toPx())
                            if (fullEffects) {
                                lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                            }
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(54.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tabsContent
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        (if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset) +
                                stretchOffset.value.x * 0.5f
                    translationY = stretchOffset.value.y * 0.55f
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { pillShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        if (fullEffects) {
                            lens(
                                10.dp.toPx() * progress,
                                14.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        } else if (midEffects) {
                            vibrancy()
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.05f, 0.05f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.05f, 0.05f)
                        val stretchX = (abs(stretchOffset.value.x) / size.width).coerceIn(0f, 1f)
                        val stretchY = (abs(stretchOffset.value.y) / size.height).coerceIn(0f, 1f)
                        scaleX *= (1f + stretchX * 0.55f) * (1f - stretchY * 0.3f)
                        scaleY *= (1f - stretchX * 0.28f) * (1f + stretchY * 0.5f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(54.dp)
                .fillMaxWidth(1f / tabs.size)
        )
        }
    }
}

@Composable
private fun RowScope.LiquidGlassNavBarTab(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    contentColor: Color,
    selectedColor: Color
) {
    val scale = LocalLiquidGlassTabScale.current
    Column(
        Modifier
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) selectedColor else contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) selectedColor else contentColor,
            maxLines = 1
        )
    }
}
