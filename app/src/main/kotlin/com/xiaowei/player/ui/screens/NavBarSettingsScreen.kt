package com.xiaowei.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xiaowei.player.R
import com.xiaowei.player.data.ThemePrefs
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.ui.LiquidGlassNavBar
import com.xiaowei.player.ui.Md3NavBar

private data class NavPreviewTab(val labelKey: String, val icon: ImageVector)

private val navPreviewTabs = listOf(
    NavPreviewTab("tab_recommend", Icons.Filled.Home),
    NavPreviewTab("tab_library", Icons.Filled.LibraryMusic),
    NavPreviewTab("tab_search", Icons.Filled.Search),
    NavPreviewTab("tab_mine", Icons.Filled.Person)
)

@Composable
fun NavBarSettingsScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = Strings.get("back"),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp).offset(x = 4.6.dp)
                        )
                    }
                }
                Text(
                    text = Strings.get("nav_bar_settings_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            NavBarSettingsContent()

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
fun NavBarSettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themePrefs = remember { ThemePrefs.get(context) }
    val currentStyle by themePrefs.navBarStyleState
    val userMaterialStyle by themePrefs.materialStyleState
    val materialFrosted = userMaterialStyle == ThemePrefs.MATERIAL_STYLE_FROSTED

    var selectedTab by remember { mutableIntStateOf(0) }
    val isMinimal = currentStyle == ThemePrefs.NAV_BAR_STYLE_MINIMAL

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val previewRes = if (isDark) {
        R.drawable.material_preview_dark
    } else {
        R.drawable.material_preview
    }

    val previewBackdrop = rememberLayerBackdrop()

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .aspectRatio(1.6f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(previewBackdrop)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = previewRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (isMinimal) {
                LiquidGlassNavBar(
                    backdrop = previewBackdrop,
                    tabs = navPreviewTabs.map { it.icon to it.labelKey },
                    selectedTabIndex = { selectedTab },
                    onTabSelected = { selectedTab = it },
                    forceFrosted = materialFrosted,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )
            } else {
                Md3NavBar(
                    tabs = navPreviewTabs.map { it.icon to it.labelKey },
                    selectedTabIndex = { selectedTab },
                    onTabSelected = { selectedTab = it },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NavBarOptionCard(
                title = Strings.get("nav_bar_style_md3"),
                selected = !isMinimal,
                onClick = {
                    themePrefs.navBarStyle = ThemePrefs.NAV_BAR_STYLE_MD3
                },
                modifier = Modifier.weight(1f)
            )
            NavBarOptionCard(
                title = Strings.get("nav_bar_style_minimal"),
                selected = isMinimal,
                onClick = {
                    themePrefs.navBarStyle = ThemePrefs.NAV_BAR_STYLE_MINIMAL
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavBarOptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 1.5.dp else 0.5.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
