package com.xiaowei.player.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaowei.player.i18n.Strings

@Composable
fun Md3NavBar(
    tabs: List<Pair<ImageVector, String>>,
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    onTabReselected: (Int) -> Unit = {}
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        windowInsets = windowInsets
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = selectedTabIndex() == index
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) {
                        onTabReselected(index)
                    } else {
                        onTabSelected(index)
                    }
                },
                icon = {
                    Icon(
                        imageVector = tab.first,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = Strings.get(tab.second),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
