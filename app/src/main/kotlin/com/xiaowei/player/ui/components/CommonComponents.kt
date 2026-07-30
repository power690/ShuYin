package com.xiaowei.player.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xiaowei.player.data.EmbeddedCoverFetcher
import com.xiaowei.player.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumCover(

    @Suppress("UNUSED_PARAMETER") coverUri: Uri?,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,

    filePath: String? = null
) {
    val context = LocalContext.current

    var currentUri by remember(filePath) {
        mutableStateOf(EmbeddedCoverFetcher.getCachedUriSync(filePath))
    }

    if (currentUri == null && !filePath.isNullOrBlank()) {
        LaunchedEffect(filePath) {
            val uri = withContext(Dispatchers.IO) {
                EmbeddedCoverFetcher.loadCoverUri(filePath, context)
            }
            currentUri = uri
        }
    }

    val imageRequest = remember(currentUri) {
        if (currentUri != null) {
            ImageRequest.Builder(context)
                .data(currentUri)
                .crossfade(false)
                .memoryCacheKey(currentUri.toString())
                .build()
        } else null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            )
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = Strings.get("album_cover"),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun GradientScrim(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color.Black.copy(alpha = 0.6f),
        Color.Black.copy(alpha = 0.0f)
    )
) {
    Box(
        modifier = modifier.background(Brush.verticalGradient(colors))
    )
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

@Composable
fun TitleSubtitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    maxLinesTitle: Int = 1,
    maxLinesSubtitle: Int = 1
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLinesTitle,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLinesSubtitle,
            overflow = TextOverflow.Ellipsis
        )
    }
}
