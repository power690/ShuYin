package com.xiaowei.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaowei.player.data.ThemePrefs
import com.xiaowei.player.i18n.Strings

// 新截图 1260x2800，比例一致 0.45，直接使用
private val STYLE_PREVIEW_RATIO = 0.45f

@Composable
fun PlayerStyleScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePrefs = androidx.compose.runtime.remember { ThemePrefs.get(context) }
    val currentStyle = themePrefs.playerStyleState.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // 标题栏：返回 + 播放页风格
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Strings.get("back"),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = Strings.get("settings_player_style"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        // 中心：两张预览图并排，左=经典样式，右=MD3
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            val gap = 16.dp
            // 高度封顶：可用高度的 55%，同时宽度不超过半宽对应的等比高度，取小者
            val imageHeight = minOf(maxHeight * 0.55f, ((maxWidth - gap) / 2) / STYLE_PREVIEW_RATIO)
            val imageWidth = imageHeight * STYLE_PREVIEW_RATIO

            Column(
                modifier = Modifier.align(BiasAlignment(horizontalBias = 0f, verticalBias = -0.3f)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StylePreviewCard(
                        label = Strings.get("player_style_md3"),
                        imageRes = com.xiaowei.player.R.drawable.player_style_md3,
                        isSelected = currentStyle == ThemePrefs.PLAYER_STYLE_MD3,
                        width = imageWidth,
                        height = imageHeight,
                        onClick = { themePrefs.playerStyle = ThemePrefs.PLAYER_STYLE_MD3 }
                    )
                    StylePreviewCard(
                        label = Strings.get("player_style_classic"),
                        imageRes = com.xiaowei.player.R.drawable.player_style_classic,
                        isSelected = currentStyle == ThemePrefs.PLAYER_STYLE_CLASSIC,
                        width = imageWidth,
                        height = imageHeight,
                        onClick = { themePrefs.playerStyle = ThemePrefs.PLAYER_STYLE_CLASSIC }
                    )
                }
            }
        }
    }
}

@Composable
private fun StylePreviewCard(
    label: String,
    imageRes: Int,
    isSelected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(imageRes),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = width, height = height)
                    .clip(RoundedCornerShape(16.dp))
                    // 放在 clip 之后：水波纹被裁成圆角、只覆盖图片区域，不延伸到下方文字
                    .clickable(onClick = onClick)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
            )
            if (isSelected) {
                // 右上角选中对勾
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
