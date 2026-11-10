package com.xiaowei.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaowei.player.i18n.Strings

private data class OpenSourceLibrary(
    val name: String,
    val author: String
)

private val OPEN_SOURCE_LIBRARIES = listOf(
    OpenSourceLibrary("Android Jetpack", "Google"),
    OpenSourceLibrary("AndroidX Media3", "Google"),
    OpenSourceLibrary("Jetpack Compose", "Google"),
    OpenSourceLibrary("Room", "Google"),
    OpenSourceLibrary("AndroidX Palette", "Google"),
    OpenSourceLibrary("AndroidX Navigation", "Google"),
    OpenSourceLibrary("Kotlin", "JetBrains"),
    OpenSourceLibrary("Kotlin Coroutines", "JetBrains"),
    OpenSourceLibrary("Coil", "coil-kt"),
    OpenSourceLibrary("OkHttp", "Square"),
    OpenSourceLibrary("mp3agic", "mpatric"),
    OpenSourceLibrary("Haze", "Chris Banes"),
    OpenSourceLibrary("AndroidLiquidGlass", "Kyant0"),
    OpenSourceLibrary("MaterialKolor", "materialkolor"),
    OpenSourceLibrary("libFLAC", "Xiph.Org Foundation"),
    OpenSourceLibrary("ExoPlayer FLAC Extension", "AndroidX Media3"),
    OpenSourceLibrary("media3-decoder-ape", "eugenedibtsev"),
    OpenSourceLibrary("Monkey's Audio", "Matthew T. Ashland"),
    OpenSourceLibrary("SPICaMusic_Android", "yangSpica27"),
    OpenSourceLibrary("jianyin", "qianqianhhh2")
)

@Composable
fun OpenSourceLicenseScreen(onBack: () -> Unit) {
    DetailScaffold(
        title = Strings.get("open_source_license"),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            Text(
                text = "Open Source License of ShuYin",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "ShuYin is powered by this open-source software.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "2026-11-07",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Edit by XiaoWei",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            for (library in OPEN_SOURCE_LIBRARIES) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(library.name)
                            }
                            append(" ")
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(library.author)
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 26.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
