package com.xiaowei.player.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    MineIconTone.ERROR -> {
        val base = MaterialTheme.colorScheme.errorContainer
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            lerp(base, MaterialTheme.colorScheme.surfaceContainerLow, 0.40f)
        } else {
            base
        }
    }
}

@Composable
private fun MineIconTone.toneContentColor(): Color = when (this) {
    MineIconTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    MineIconTone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    MineIconTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
    MineIconTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
}

@Composable
private fun MineEntryCard(
    icon: ImageVector,
    tone: MineIconTone,
    title: String,
    iconSize: Dp,
    titleStyle: TextStyle,
    valueText: String? = null,
    modifier: Modifier = Modifier,
    valueBaseFontSize: Float = 16f,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "mineEntryCardScale"
    )
    var valueFontSize by remember(valueText) { mutableFloatStateOf(valueBaseFontSize) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(tone.toneContainerColor())
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.toneContentColor(),
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = tone.toneContentColor().copy(alpha = 0.72f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (valueText != null) {
                Text(
                    text = valueText,
                    fontSize = valueFontSize.sp,
                    color = tone.toneContentColor(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { result ->
                        if (result.hasVisualOverflow) {
                            valueFontSize = (valueFontSize - 0.5f).coerceAtLeast(12f)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MineProfileCard(
    userName: String,
    avatarUri: Uri?,
    userLevel: UserProfileRepository.UserLevel,
    scale: Float,
    interactionSource: MutableInteractionSource,
    onLevelClick: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(32.dp))
            .background(MineIconTone.SECONDARY.toneContainerColor())
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(60.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val av = avatarUri
                if (av != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
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
                        tint = MineIconTone.SECONDARY.toneContentColor(),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text(
                text = userName,
                style = MaterialTheme.typography.titleLarge,
                color = MineIconTone.SECONDARY.toneContentColor(),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = when (userLevel) {
                    UserProfileRepository.UserLevel.NORMAL -> Strings.get("user_level_normal")
                    UserProfileRepository.UserLevel.ADVANCED -> Strings.get("user_level_advanced")
                    UserProfileRepository.UserLevel.SUPREME -> Strings.get("user_level_supreme")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MineIconTone.SECONDARY.toneContentColor().copy(alpha = 0.72f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLevelClick
                )
            )
        }
    }
}

@Composable
fun MineScreen(
    onOpenFavorite: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    favoriteCount: Int = 0,
    songCount: Int = 0,
    bottomPadding: Dp = 168.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val profileRepo = remember { UserProfileRepository(context, AppDatabase.get(context)) }
    var userName by remember { mutableStateOf(Strings.get("mine_user")) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var userLevel by remember { mutableStateOf(UserProfileRepository.UserLevel.NORMAL) }
    var hasCustomName by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showBenefitsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(songCount) {
        val profile = withContext(Dispatchers.IO) { profileRepo.get() }
        userName = profile.name
        avatarUri = profile.avatarUri
        userLevel = profile.level
        hasCustomName = profile.hasCustomName
    }

    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (cardPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "mineCardScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
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

        MineProfileCard(
            userName = if (hasCustomName) userName else Strings.get("mine_user"),
            avatarUri = avatarUri,
            userLevel = userLevel,
            scale = cardScale,
            interactionSource = cardInteraction,
            onLevelClick = { showBenefitsDialog = true },
            onClick = { showEditDialog = true }
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MineEntryCard(
                icon = Icons.Outlined.FavoriteBorder,
                tone = MineIconTone.ERROR,
                title = Strings.get("favorite"),
                iconSize = 28.dp,
                titleStyle = MaterialTheme.typography.labelMedium,
                valueText = Strings.get("library_song_count", favoriteCount),
                valueBaseFontSize = 16f,
                modifier = Modifier.weight(1f),
                onClick = { onOpenFavorite() }
            )
            MineEntryCard(
                icon = Icons.Outlined.Settings,
                tone = MineIconTone.SECONDARY,
                title = Strings.get("settings_title"),
                iconSize = 28.dp,
                titleStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                onClick = { onOpenSettings() }
            )
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            initialName = if (hasCustomName) userName else "",
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
                    userLevel = profile.level
                    hasCustomName = profile.hasCustomName
                    showEditDialog = false
                }
            }
        )
    }

    if (showBenefitsDialog) {
        UserBenefitsDialog(
            onDismiss = { showBenefitsDialog = false }
        )
    }
    }
}

@Composable
private fun UserBenefitsDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = Strings.get("user_benefits_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = Strings.get("user_level_normal"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "\u2022 " + Strings.get("user_benefits_normal_bullets"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = Strings.get("user_level_advanced"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "\u2022 " + Strings.get("user_benefits_advanced_1"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\u2022 " + Strings.get("user_benefits_advanced_gate"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\u00b7 " + Strings.get("user_benefits_advanced_req_1"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Text(
                    text = "\u00b7 " + Strings.get("user_benefits_advanced_req_2"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = Strings.get("user_level_supreme"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "\u2022 " + Strings.get("user_benefits_supreme_1"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\u2022 " + Strings.get("user_benefits_supreme_2"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\u2022 " + Strings.get("user_benefits_supreme_gate"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\u2022 " + Strings.get("user_benefits_checkin_title"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\u00b7 " + Strings.get("user_benefits_checkin_1"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Text(
                    text = "\u00b7 " + Strings.get("user_benefits_checkin_2"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Text(
                    text = "\u00b7 " + Strings.get("user_benefits_checkin_3"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Text(
                    text = "\u00b7 " + Strings.get("user_benefits_checkin_4"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = Strings.get("user_benefits_feedback"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = Strings.get("user_benefits_qq_group"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = Strings.get("user_benefits_got_it"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
