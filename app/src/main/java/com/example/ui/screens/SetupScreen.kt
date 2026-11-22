package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.RepoConfig

@Composable
fun SetupScreen(
    existingConfig: RepoConfig?,
    isConnecting: Boolean,
    errorMessage: String?,
    onConnect: (repo: String, token: String, branch: String) -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var repoInput by rememberSaveable(existingConfig) {
        mutableStateOf(existingConfig?.fullName.orEmpty())
    }
    var tokenInput by rememberSaveable(existingConfig) {
        mutableStateOf(existingConfig?.token.orEmpty())
    }
    var branchInput by rememberSaveable(existingConfig) {
        mutableStateOf(existingConfig?.branch.orEmpty())
    }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var guideExpanded by rememberSaveable { mutableStateOf(false) }

    if (onCancel != null) {
        BackHandler(onBack = onCancel)
    }

    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (onCancel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("setup_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "重新配置仓库",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onBackground
                    )
                }
            }

            // Editorial Warm Paper Hero Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_setup_header_1795331820679),
                    contentDescription = "暖纸笔记本插画",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xB81E1813)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color(0xFFFFFBF5)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.setup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEBE2D5)
                    )
                }
            }

            // Form Card
            Surface(
                color = colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outlineVariant,
                        focusedContainerColor = colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = colorScheme.surfaceContainerLow
                    )

                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text(stringResource(R.string.setup_repo_label)) },
                        placeholder = { Text(stringResource(R.string.setup_repo_placeholder)) },
                        singleLine = true,
                        enabled = !isConnecting,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_repo_input")
                    )

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text(stringResource(R.string.setup_token_label)) },
                        placeholder = { Text(stringResource(R.string.setup_token_placeholder)) },
                        singleLine = true,
                        enabled = !isConnecting,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { tokenVisible = !tokenVisible },
                                modifier = Modifier.testTag("setup_toggle_token_visibility")
                            ) {
                                Icon(
                                    imageVector = if (tokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (tokenVisible) "隐藏 Token" else "显示 Token",
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_token_input")
                    )

                    OutlinedTextField(
                        value = branchInput,
                        onValueChange = { branchInput = it },
                        label = { Text(stringResource(R.string.setup_branch_label)) },
                        placeholder = { Text(stringResource(R.string.setup_branch_placeholder)) },
                        singleLine = true,
                        enabled = !isConnecting,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!isConnecting) {
                                    onConnect(repoInput, tokenInput, branchInput)
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_branch_input")
                    )

                    AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                        Surface(
                            color = colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { onConnect(repoInput, tokenInput, branchInput) },
                        enabled = !isConnecting && repoInput.isNotBlank() && tokenInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("setup_connect_button")
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.setup_connecting))
                        } else {
                            Text(
                                text = stringResource(R.string.setup_connect_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Collapsible Token Guide
            Surface(
                color = colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { guideExpanded = !guideExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("setup_token_guide_toggle"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.setup_token_guide_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (guideExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (guideExpanded) "收起指引" else "展开指引",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = guideExpanded) {
                        Text(
                            text = stringResource(R.string.setup_token_guide_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                        )
                    }
                }
            }

            // Privacy footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.setup_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
