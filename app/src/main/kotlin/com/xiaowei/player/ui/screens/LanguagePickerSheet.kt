package com.xiaowei.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaowei.player.i18n.Strings

private data class LanguageOption(
    val code: String?,
    val displayName: String,
)

private val LANGUAGE_OPTIONS = listOf(
    LanguageOption(null, ""),  

    LanguageOption("zh", "简体中文"),
    LanguageOption("zh-TW", "繁體中文 (台灣)"),
    LanguageOption("zh-HK", "繁體中文 (香港)"),
    LanguageOption("zh-MO", "中文 (澳門)"),

    LanguageOption("en", "English"),
    LanguageOption("fr", "français (France)"),
    LanguageOption("de", "Deutsch (Deutschland)"),
    LanguageOption("es", "español (España)"),
    LanguageOption("pt", "português (Brasil)"),
    LanguageOption("it", "italiano (Italia)"),
    LanguageOption("ru", "русский (Россия)"),
    LanguageOption("pl", "polski (Polska)"),
    LanguageOption("uk", "українська (Україна)"),
    LanguageOption("nl", "Nederlands (Nederland)"),
    LanguageOption("sv", "svenska (Sverige)"),
    LanguageOption("cs", "čeština (Česko)"),
    LanguageOption("hu", "magyar (Magyarország)"),
    LanguageOption("el", "Ελληνικά (Ελλάδα)"),
    LanguageOption("ro", "română (România)"),
    LanguageOption("fi", "suomi (Suomi)"),
    LanguageOption("da", "dansk (Danmark)"),
    LanguageOption("nb", "norsk (Norge)"),
    LanguageOption("ms", "Bahasa Melayu (Malaysia)"),
    LanguageOption("tl", "Tagalog (Pilipinas)"),
    LanguageOption("tr", "Türkçe (Türkiye)"),
    LanguageOption("vi", "Tiếng Việt (Việt Nam)"),
    LanguageOption("in", "Bahasa Indonesia"),

    LanguageOption("ja", "日本語"),
    LanguageOption("ko", "한국어"),

    LanguageOption("hi", "हिन्दी (भारत)"),
    LanguageOption("bn", "বাংলা (ভারত)"),
    LanguageOption("th", "ไทย (ไทย)"),
    LanguageOption("mn", "Монгол (Монгол)"),

    LanguageOption("ar", "العربية (المملكة العربية السعودية)"),
    LanguageOption("fa", "فارسی (ایران)"),
    LanguageOption("ur", "اردو (پاکستان)"),
    LanguageOption("ug", "ئۇيغۇرچە"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    currentLangCode: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = null  
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp)
        ) {

            Text(
                text = Strings.get("settings_language"),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Spacer(Modifier.size(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                items(LANGUAGE_OPTIONS) { option ->
                    val isSelected = option.code == currentLangCode

                    val label = if (option.code == null) {
                        Strings.get("language_follow_system")
                    } else {
                        option.displayName
                    }
                    LanguageRow(
                        label = label,
                        isSelected = isSelected,
                        onClick = { onConfirm(option.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}
