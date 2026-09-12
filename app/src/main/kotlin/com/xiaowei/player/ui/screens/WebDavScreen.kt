package com.xiaowei.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaowei.player.data.WebDavAccount
import com.xiaowei.player.data.WebDavPrefs
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.ui.components.M3ExpressiveSwitch

@Composable
fun WebDavScreen(
    onBack: () -> Unit,
    onWebDavChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val webDavPrefs = remember { WebDavPrefs.get(context) }
    val accounts by webDavPrefs.accountsState
    val activeId by webDavPrefs.activeIdState

    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<WebDavAccount?>(null) }
    var deletingAccount by remember { mutableStateOf<WebDavAccount?>(null) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Strings.get("back"),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = Strings.get("webdav_title"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 96.dp)
        ) {
            for (account in accounts) {
                val isSelfActive = activeId == account.id
                val anyActive = activeId != null
                val switchEnabled = !anyActive || isSelfActive
                WebDavAccountItem(
                    account = account,
                    checked = isSelfActive,
                    switchEnabled = switchEnabled,
                    showDivider = account != accounts.last(),
                    onCheckedChange = { checked ->
                        webDavPrefs.setActive(if (checked) account.id else null)
                        onWebDavChanged(checked)
                    },
                    onEdit = { editingAccount = account },
                    onDelete = { deletingAccount = account }
                )
            }

            WebDavAddItem(
                enabled = true,
                onClick = { showAddDialog = true }
            )
        }
    }

    if (showAddDialog) {
        WebDavAddDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, username, password, path ->
                webDavPrefs.addAccount(name, url, username, password, path)
                showAddDialog = false
            }
        )
    }

    if (editingAccount != null) {
        WebDavAddDialog(
            initial = editingAccount,
            onDismiss = { editingAccount = null },
            onConfirm = { name, url, username, password, path ->
                editingAccount?.let { account ->
                    webDavPrefs.updateAccount(account.id, name, url, username, password, path)
                    if (webDavPrefs.activeId == account.id) {
                        onWebDavChanged(true)
                    }
                }
                editingAccount = null
            }
        )
    }

    deletingAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            title = {
                Text(
                    text = Strings.get("webdav_delete_title"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = Strings.get("webdav_delete_confirm", account.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val wasActive = webDavPrefs.activeId == account.id
                        webDavPrefs.removeAccount(account.id)
                        deletingAccount = null
                        if (wasActive) {
                            onWebDavChanged(false)
                        }
                    }
                ) {
                    Text(Strings.get("webdav_delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAccount = null }) {
                    Text(Strings.get("cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

private enum class WebDavIconTone {
    PRIMARY, SECONDARY, TERTIARY
}

@Composable
private fun WebDavIconTone.toneContainerColor() = when (this) {
    WebDavIconTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    WebDavIconTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    WebDavIconTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun WebDavIconTone.toneContentColor() = when (this) {
    WebDavIconTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    WebDavIconTone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    WebDavIconTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
}

@Composable
private fun WebDavIconBadge(
    icon: ImageVector,
    tone: WebDavIconTone
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
private fun WebDavPressableRow(
    enabled: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "webdavItemScale"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onClick != null && enabled) {
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = ripple(),
                            onLongClick = onLongClick,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}

@Composable
private fun WebDavAccountItem(
    account: WebDavAccount,
    checked: Boolean,
    switchEnabled: Boolean,
    showDivider: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    WebDavPressableRow(enabled = true, onClick = onEdit, onLongClick = onDelete) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WebDavIconBadge(icon = Icons.Outlined.Cloud, tone = WebDavIconTone.PRIMARY)
            Spacer(Modifier.width(12.dp))
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            M3ExpressiveSwitch(
                checked = checked,
                enabled = switchEnabled,
                onCheckedChange = if (switchEnabled) onCheckedChange else null
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
private fun WebDavAddItem(
    enabled: Boolean,
    onClick: () -> Unit
) {
    WebDavPressableRow(enabled = enabled, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WebDavIconBadge(icon = Icons.Outlined.Add, tone = WebDavIconTone.SECONDARY)
            Spacer(Modifier.width(12.dp))
            Text(
                text = Strings.get("webdav_add"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WebDavAddDialog(
    initial: WebDavAccount?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var url by remember(initial) { mutableStateOf(initial?.url ?: "") }
    var username by remember(initial) { mutableStateOf(initial?.username ?: "") }
    var password by remember(initial) { mutableStateOf(initial?.password ?: "") }
    var path by remember(initial) { mutableStateOf(initial?.path ?: "") }
    var passwordVisible by remember(initial) { mutableStateOf(false) }

    val allFilled = name.isNotBlank() && url.isNotBlank() && username.isNotBlank() &&
        password.isNotBlank()

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
                .padding(horizontal = 36.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text(
                    text = Strings.get(if (initial != null) "webdav_edit_title" else "webdav_dialog_title"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                WebDavTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = Strings.get("webdav_name"),
                    placeholder = Strings.get("webdav_name_hint")
                )

                Spacer(Modifier.height(10.dp))

                WebDavTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = Strings.get("webdav_url"),
                    placeholder = Strings.get("webdav_url_hint")
                )

                Spacer(Modifier.height(10.dp))

                WebDavTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = Strings.get("webdav_username"),
                    placeholder = Strings.get("webdav_username_hint")
                )

                Spacer(Modifier.height(10.dp))

                WebDavTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = Strings.get("webdav_password"),
                    placeholder = Strings.get("webdav_password_hint"),
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
                )

                Spacer(Modifier.height(10.dp))

                WebDavTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = Strings.get("webdav_path"),
                    placeholder = Strings.get("webdav_path_hint")
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = Strings.get("cancel"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = allFilled,
                        onClick = {
                            onConfirm(name, url, username, password, path)
                        }
                    ) {
                        Text(
                            text = Strings.get("confirm"),
                            color = if (allFilled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebDavTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { onTogglePasswordVisibility?.invoke() }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = if (isPassword) ImeAction.Done else ImeAction.Next),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}
