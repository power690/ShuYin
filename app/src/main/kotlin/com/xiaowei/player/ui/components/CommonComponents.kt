package com.xiaowei.player.ui.components

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xiaowei.player.data.EmbeddedCoverFetcher
import com.xiaowei.player.data.Song
import com.xiaowei.player.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumCover(

    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,
    coverSizePx: Int = 128,

    filePath: String? = null
) {
    val context = LocalContext.current

    var coverBytes by remember(filePath) {
        mutableStateOf(EmbeddedCoverFetcher.getCachedBytesSync(filePath))
    }

    if (coverBytes == null && !filePath.isNullOrBlank()) {
        LaunchedEffect(filePath) {
            val bytes = withContext(Dispatchers.IO) {
                EmbeddedCoverFetcher.loadCoverBytes(filePath)
            }
            coverBytes = bytes
        }
    }

    val imageRequest = remember(coverBytes, coverSizePx) {
        if (coverBytes != null && !filePath.isNullOrBlank()) {
            ImageRequest.Builder(context)
                .data(coverBytes)
                .crossfade(false)
                .size(coverSizePx)
                .memoryCacheKey("cover_$coverSizePx\u0000$filePath")
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
fun PlayAllButton(
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "playAllButtonScale"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onPlayAll
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = Strings.get("play_all"),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = Strings.get("play_all"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

enum class SortOption(val labelKey: String) {
    DEFAULT("sort_default"),
    TITLE_AZ("sort_title_az"),
    TITLE_ZA("sort_title_za"),
    ARTIST_AZ("sort_artist_az"),
    ARTIST_ZA("sort_artist_za"),
    ALBUM_AZ("sort_album_az"),
    ALBUM_ZA("sort_album_za"),
    DURATION_ASC("sort_duration_asc"),
    DURATION_DESC("sort_duration_desc"),
    SIZE_ASC("sort_size_asc"),
    SIZE_DESC("sort_size_desc"),
    DATE_ADDED_DESC("sort_date_added_desc"),
    DATE_ADDED_ASC("sort_date_added_asc")
}

fun sortSongs(songs: List<Song>, option: SortOption): List<Song> = when (option) {
    SortOption.DEFAULT -> songs
    SortOption.TITLE_AZ -> songs.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
    SortOption.TITLE_ZA -> songs.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.reversed())
    SortOption.ARTIST_AZ -> songs.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.displayArtist })
    SortOption.ARTIST_ZA -> songs.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.displayArtist }.reversed())
    SortOption.ALBUM_AZ -> songs.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.displayAlbum })
    SortOption.ALBUM_ZA -> songs.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.displayAlbum }.reversed())
    SortOption.DURATION_ASC -> songs.sortedBy { it.duration }
    SortOption.DURATION_DESC -> songs.sortedByDescending { it.duration }
    SortOption.SIZE_ASC -> songs.sortedBy { it.size }
    SortOption.SIZE_DESC -> songs.sortedByDescending { it.size }
    SortOption.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
    SortOption.DATE_ADDED_ASC -> songs.sortedBy { it.dateAdded }
}

@Composable
fun SortButton(
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    var menuWidthPx by remember { mutableStateOf(with(density) { 200.dp.roundToPx() }) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "sortButtonScale"
    )
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .onGloballyPositioned { anchorWidthPx = it.size.width }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { expanded = true }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = Strings.get("sort_by"),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = Strings.get(sortOption.labelKey),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val offsetX = with(density) { (anchorWidthPx - menuWidthPx).toDp() }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(offsetX, 0.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 150.dp)
                    .onGloballyPositioned { menuWidthPx = it.size.width }
                    .heightIn(max = 144.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val groups = listOf(
                    listOf(SortOption.DEFAULT),
                    listOf(
                        SortOption.TITLE_AZ,
                        SortOption.TITLE_ZA,
                        SortOption.ARTIST_AZ,
                        SortOption.ARTIST_ZA,
                        SortOption.ALBUM_AZ,
                        SortOption.ALBUM_ZA
                    ),
                    listOf(
                        SortOption.DURATION_ASC,
                        SortOption.DURATION_DESC,
                        SortOption.SIZE_ASC,
                        SortOption.SIZE_DESC,
                        SortOption.DATE_ADDED_DESC,
                        SortOption.DATE_ADDED_ASC
                    )
                )
                groups.forEach { group ->
                    group.forEach { option ->
                        SortMenuItem(
                            option = option,
                            selected = option == sortOption,
                            onClick = {
                                onSortOptionChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    option: SortOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Strings.get(option.labelKey),
            style = MaterialTheme.typography.labelLarge,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun M3ExpressiveSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val handleSize by animateDpAsState(
        targetValue = when {
            pressed -> 28.dp
            checked -> 24.dp
            else -> 16.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "switchHandleSize"
    )
    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val edgeSeam = 6.dp
    val handleOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - edgeSeam - handleSize else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "switchHandleOffset"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "switchTrackColor"
    )
    val handleColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.outline,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "switchHandleColor"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange?.invoke(!checked) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(trackWidth, trackHeight)
                .background(trackColor, CircleShape)
                .border(
                    width = if (checked) 0.dp else 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
                .alpha(if (enabled) 1f else 0.38f)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = handleOffset)
                    .size(handleSize)
                    .background(handleColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                }
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
