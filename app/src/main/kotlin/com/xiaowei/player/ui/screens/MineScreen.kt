package com.xiaowei.player.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xiaowei.player.data.UserProfileRepository
import com.xiaowei.player.data.db.AppDatabase
import com.xiaowei.player.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MineIconTone {
    PRIMARY, SECONDARY, TERTIARY, ERROR
}

@Composable
private fun MineIconTone.toneContainerColor(): Color = when (this) {
    MineIconTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    MineIconTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    MineIconTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
    MineIconTone.ERROR -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun MineIconTone.toneContentColor(): Color = when (this) {
    MineIconTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    MineIconTone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    MineIconTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
    MineIconTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
}

@Composable
private fun MineIconBadge(
    icon: ImageVector,
    tone: MineIconTone
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(tone.toneContainerColor()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.toneContentColor(),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MineActionItem(
    icon: ImageVector,
    tone: MineIconTone,
    title: String,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "mineItemScale"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MineIconBadge(icon = icon, tone = tone)
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun MineScreen(
    onOpenFavorite: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenLyricSynth: () -> Unit = {},
    bottomPadding: Dp = 168.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val profileRepo = remember { UserProfileRepository(AppDatabase.get(context)) }
    var userName by remember { mutableStateOf(Strings.get("mine_user")) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val profile = withContext(Dispatchers.IO) { profileRepo.get() }
        userName = profile.name
        avatarUri = profile.avatarUri
    }

    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (cardPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "mineCardScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottomPadding)
    ) {
        Text(
            text = Strings.get("mine_title"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .clickable(
                    interactionSource = cardInteraction,
                    indication = ripple(),
                    onClick = { showEditDialog = true }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(60.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val av = avatarUri
                    if (av != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(av)
                                .crossfade(true)
                                .build(),
                            contentDescription = Strings.get("mine_avatar"),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = Strings.get("mine_avatar"),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
            Text(
                text = userName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(8.dp))

        MineActionItem(
            icon = Icons.Outlined.FavoriteBorder,
            tone = MineIconTone.ERROR,
            title = Strings.get("favorite"),
            showDivider = false,
            onClick = { onOpenFavorite() }
        )

        MineActionItem(
            icon = Icons.Outlined.Settings,
            tone = MineIconTone.SECONDARY,
            title = Strings.get("settings_title"),
            showDivider = false,
            onClick = { onOpenSettings() }
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            initialName = userName,
            initialAvatarUri = avatarUri,
            onDismiss = { showEditDialog = false },
            onConfirm = { newName, newAvatarUri ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        profileRepo.saveName(newName)
                        profileRepo.saveAvatarUri(newAvatarUri)
                    }
                    val profile = withContext(Dispatchers.IO) { profileRepo.get() }
                    userName = profile.name
                    avatarUri = profile.avatarUri
                    showEditDialog = false
                }
            }
        )
    }
}
