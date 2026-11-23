package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppUpdateConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.model.UpdateDownloadState
import java.util.Locale

@Composable
fun UpdateConfigDialog(
    existingConfig: AppUpdateConfig?,
    errorMessage: String?,
    onSave: (repoInput: String, tokenInput: String, autoCheck: Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var repoInput by remember(existingConfig) { mutableStateOf(existingConfig?.fullName.orEmpty()) }
    var tokenInput by remember(existingConfig) { mutableStateOf(existingConfig?.token.orEmpty()) }
    var autoCheck by remember(existingConfig) { mutableStateOf(existingConfig?.autoCheckOnLaunch ?: true) }
    var showToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceContainerLowest,
        title = {
            Text(
                text = stringResource(R.string.update_config_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text(stringResource(R.string.update_repo_label)) },
                    placeholder = { Text(stringResource(R.string.update_repo_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_repo_input")
                )

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text(stringResource(R.string.update_token_label)) },
                    placeholder = { Text(stringResource(R.string.update_token_hint)) },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showToken) "隐藏 Token" else "显示 Token"
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_token_input")
                )

                Text(
                    text = stringResource(R.string.update_token_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceContainerLow)
                        .clickable { autoCheck = !autoCheck }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.update_auto_check_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoCheck,
                        onCheckedChange = { autoCheck = it },
                        modifier = Modifier.testTag("update_auto_check_switch")
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(repoInput, tokenInput, autoCheck) },
                enabled = repoInput.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("update_config_save_button")
            ) {
                Text(stringResource(R.string.update_save_config))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (existingConfig != null) {
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error),
                        modifier = Modifier.testTag("update_config_clear_button")
                    ) {
                        Text(stringResource(R.string.update_clear_config))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.note_cancel))
                }
            }
        }
    )
}

@Composable
fun UpdateAvailableDialog(
    updateInfo: AppUpdateInfo,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val hasDirectApk = updateInfo.apkAssetId != null || !updateInfo.apkApiUrl.isNullOrBlank()
    val isDownloading = downloadState is UpdateDownloadState.Downloading
    var needsUnknownSourcePermission by remember { mutableStateOf(false) }
    var pendingInstallPath by remember { mutableStateOf<String?>(null) }

    fun triggerInstall(apkPath: String) {
        if (ApkInstaller.canInstallPackages(context)) {
            needsUnknownSourcePermission = false
            ApkInstaller.launchInstall(context, apkPath).onFailure { err ->
                onInstallError(err.message ?: "无法拉起系统安装器")
            }
        } else {
            needsUnknownSourcePermission = true
            pendingInstallPath = apkPath
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val path = pendingInstallPath
        if (path != null && ApkInstaller.canInstallPackages(context)) {
            needsUnknownSourcePermission = false
            pendingInstallPath = null
            ApkInstaller.launchInstall(context, path).onFailure { err ->
                onInstallError(err.message ?: "无法拉起系统安装器")
            }
        }
    }

    // Auto-trigger system installer once download finishes
    LaunchedEffect(downloadState) {
        if (downloadState is UpdateDownloadState.ReadyToInstall) {
            triggerInstall(downloadState.apkFilePath)
        } else {
            needsUnknownSourcePermission = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading
        ),
        containerColor = colorScheme.surfaceContainerLowest,
        icon = {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = if (updateInfo.isNewer) {
                    stringResource(R.string.update_available_title)
                } else {
                    "GitHub Release · ${updateInfo.tagName}"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = updateInfo.tagName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "当前 v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                if (!updateInfo.apkAssetName.isNullOrBlank()) {
                    Surface(
                        color = colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Android,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = updateInfo.apkAssetName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurface
                                )
                                if (updateInfo.apkSizeBytes > 0L) {
                                    Text(
                                        text = formatByteSize(updateInfo.apkSizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    Text(
                        text = updateInfo.releaseNotes.ifBlank { stringResource(R.string.update_no_notes) },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }

                // Download status section
                when (downloadState) {
                    is UpdateDownloadState.Idle -> Unit

                    is UpdateDownloadState.Downloading -> {
                        Surface(
                            color = colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.update_downloading_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurface
                                    )
                                    val pct = downloadState.progress?.let { "${(it * 100).toInt()}%" }
                                    if (pct != null) {
                                        Text(
                                            text = pct,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colorScheme.primary
                                        )
                                    }
                                }
                                val progress = downloadState.progress
                                if (progress != null) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                }
                                val sizeText = if (downloadState.totalBytes > 0L) {
                                    "${formatByteSize(downloadState.downloadedBytes)} / ${formatByteSize(downloadState.totalBytes)}"
                                } else {
                                    formatByteSize(downloadState.downloadedBytes)
                                }
                                Text(
                                    text = sizeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Surface(
                            color = colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.update_download_complete_label),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurface
                                    )
                                }
                                if (needsUnknownSourcePermission) {
                                    Text(
                                        text = stringResource(R.string.update_permission_needed_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Surface(
                            color = colorScheme.errorContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = downloadState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                isDownloading -> {
                    Button(
                        onClick = onCancelDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("update_cancel_download_button")
                    ) {
                        Text(stringResource(R.string.update_cancel_download_button))
                    }
                }

                downloadState is UpdateDownloadState.ReadyToInstall -> {
                    Button(
                        onClick = {
                            if (ApkInstaller.canInstallPackages(context)) {
                                triggerInstall(downloadState.apkFilePath)
                            } else {
                                pendingInstallPath = downloadState.apkFilePath
                                needsUnknownSourcePermission = true
                                runCatching {
                                    unknownSourcesLauncher.launch(
                                        ApkInstaller.createUnknownSourcesSettingsIntent(context)
                                    )
                                }.onFailure {
                                    onInstallError("无法打开系统安装权限设置，请在系统设置中手动允许安装未知应用")
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("update_install_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (needsUnknownSourcePermission) "前往授权安装" else stringResource(R.string.update_install_now_button)
                        )
                    }
                }

                hasDirectApk -> {
                    Button(
                        onClick = onStartDownload,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("update_download_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (downloadState is UpdateDownloadState.Error) {
                                stringResource(R.string.update_retry_download_button)
                            } else {
                                stringResource(R.string.update_download_now_button)
                            }
                        )
                    }
                }

                else -> {
                    Button(
                        onClick = {
                            runCatching { uriHandler.openUri(updateInfo.releaseUrl) }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("update_open_release_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.update_open_release_button))
                    }
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (hasDirectApk && downloadState !is UpdateDownloadState.ReadyToInstall) {
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri(updateInfo.releaseUrl) } },
                            modifier = Modifier.testTag("update_open_browser_button")
                        ) {
                            Text(stringResource(R.string.update_open_release_button))
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_later_button")
                    ) {
                        Text(stringResource(R.string.update_later_button))
                    }
                }
            }
        }
    )
}

private fun formatByteSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
