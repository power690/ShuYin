package com.xiaowei.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
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

private data class MaterialTab(val labelKey: String, val icon: ImageVector)

private val materialTabs = listOf(
    MaterialTab("tab_recommend", Icons.Filled.Home),
    MaterialTab("tab_library", Icons.Filled.LibraryMusic),
    MaterialTab("tab_mine", Icons.Filled.Person)
)

@Composable
fun MaterialSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val themePrefs = remember { ThemePrefs.get(context) }
    val currentStyle by themePrefs.materialStyleState

    var selectedTab by remember { mutableIntStateOf(0) }
    val isFrosted = currentStyle == ThemePrefs.MATERIAL_STYLE_FROSTED

    val previewBackdrop = rememberLayerBackdrop()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Strings.get("back"),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = Strings.get("material_settings_title"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }

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
                    painter = painterResource(id = R.drawable.material_preview),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            LiquidGlassNavBar(
                backdrop = previewBackdrop,
                tabs = materialTabs.map { it.icon to it.labelKey },
                selectedTabIndex = { selectedTab },
                onTabSelected = { selectedTab = it },
                forceFrosted = isFrosted,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MaterialOptionCard(
                title = Strings.get("material_settings_liquid"),
                selected = !isFrosted,
                onClick = {
                    themePrefs.materialStyle = ThemePrefs.MATERIAL_STYLE_LIQUID
                },
                modifier = Modifier.weight(1f)
            )
            MaterialOptionCard(
                title = Strings.get("material_settings_frosted"),
                selected = isFrosted,
                onClick = {
                    themePrefs.materialStyle = ThemePrefs.MATERIAL_STYLE_FROSTED
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun MaterialOptionCard(
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
