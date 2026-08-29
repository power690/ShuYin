package com.xiaowei.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.xiaowei.player.data.Song
import com.xiaowei.player.ui.components.AlbumCover
import com.xiaowei.player.R
import com.xiaowei.player.i18n.Strings

@Composable
fun MiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onClick: () -> Unit,
    glassBackdrop: Backdrop? = null
) {

    if (glassBackdrop != null && LiquidGlassEnabled) {
        val glassShape = RoundedCornerShape(26.dp)
        val surfaceColor =
            if (LiquidGlassFullEffects) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            } else if (LiquidGlassMidEffects) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
            } else {
                val base =
                    if (MaterialTheme.colorScheme.surface.luminance() >= 0.5f) Color(0xFFFAFAFA)
                    else Color(0xFF121212)
                lerp(
                    base,
                    MaterialTheme.colorScheme.primary,
                    if (MaterialTheme.colorScheme.surface.luminance() >= 0.5f) 0.10f else 0.12f
                )
            }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .drawBackdrop(
                    backdrop = glassBackdrop,
                    shape = { glassShape },
                    effects = {
                        vibrancy()
                        blur(if (LiquidGlassFullEffects) 8.dp.toPx() else 14.dp.toPx())
                        if (LiquidGlassFullEffects) {
                            lens(
                                24.dp.toPx(),
                                24.dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = { Highlight.Default.copy(width = 1.dp) },
                    shadow = { Shadow(radius = 20.dp, alpha = 0.8f) },
                    innerShadow = {
                        InnerShadow(
                            radius = 10.dp,
                            offset = DpOffset(0.dp, 5.dp),
                            alpha = 0.55f
                        )
                    },
                    onDrawSurface = { drawRect(surfaceColor) }
                )
                .clickable(onClick = onClick)
        ) {
            MiniPlayerContent(
                song = song,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onPlayPause = onPlayPause,
                onPrev = onPrev,
                onNext = onNext,
                onSeek = onSeek
            )
        }
    } else {
        Surface(
            tonalElevation = 1.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
            ) {
                MiniPlayerContent(
                    song = song,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPlayPause = onPlayPause,
                    onPrev = onPrev,
                    onNext = onNext,
                    onSeek = onSeek
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun MiniPlayerContent(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var draggingValue by remember { mutableStateOf<Float?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(
            coverUri = song.albumArtUri,
            modifier = Modifier.size(40.dp),
            cornerRadius = 7,
            filePath = song.data
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.displayAlbumDashArtist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
        Spacer(Modifier.width(8.dp))
        MiniPlayerButton(
            onClick = onPrev,
            icon = Icons.Filled.SkipPrevious,
            description = Strings.get("previous"),
            tint = MaterialTheme.colorScheme.onSurface
        )
        MiniPlayerButton(
            onClick = onPlayPause,
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = if (isPlaying) Strings.get("pause") else Strings.get("play"),
            tint = MaterialTheme.colorScheme.onSurface
        )
        MiniPlayerButton(
            onClick = onNext,
            icon = Icons.Filled.SkipNext,
            description = Strings.get("next"),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }

    val progress = draggingValue ?: if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .height(20.dp)
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput

                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((ratio * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput

                detectHorizontalDragGestures(
                    onDragEnd = {
                        draggingValue?.let { v ->
                            onSeek((v * durationMs).toLong())
                        }
                        draggingValue = null
                    },
                    onDragCancel = {
                        draggingValue = null
                    }
                ) { change, _ ->
                    val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                    draggingValue = ratio
                }
            },
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    }
}
