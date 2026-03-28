package app.ui

import app.SetupUiState
import app.SetupViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.composeapp.generated.resources.*
import com.familymessenger.contract.SetupInviteSummary
import com.familymessenger.contract.UserRole
import org.jetbrains.compose.resources.stringResource

// ── Root ──────────────────────────────────────────────────────────────────────
@Composable
internal fun SetupScreen(viewModel: SetupViewModel) {
    val state by viewModel.state.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SetupBg),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StepProgressBar(currentStep = state.step)

            when (state.step) {
                1    -> StepPassword(state, viewModel)
                2    -> StepMembers(state, viewModel)
                else -> StepInvites(state, viewModel)
            }
        }
    }
}

// ── Step progress bar ─────────────────────────────────────────────────────────
@Composable
private fun StepProgressBar(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val step = index + 1
            val isDone   = step < currentStep
            val isActive = step == currentStep

            // Dot
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isDone || isActive -> TgBlue
                            else               -> SurfaceBg
                        },
                    )
                    .then(
                        if (!isDone && !isActive) Modifier.border(0.5.dp, CardBorder, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isDone) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(
                        text = "$step",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) Color.White else TextSecondary,
                        lineHeight = 12.sp,
                    )
                }
            }

            // Line between dots
            if (index < 2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (step < currentStep) TgBlue else CardBorder),
                )
            }
        }
    }
}

// ── Step 1 — master password ──────────────────────────────────────────────────
@Composable
private fun StepPassword(state: SetupUiState, viewModel: SetupViewModel) {
    SetupCard {
        Text(stringResource(Res.string.setup_password_title), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.setup_password_description),
            fontSize = 14.sp,
            color = TextSecondary,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(4.dp))

        // Warning block
        Surface(
            color = WarnBg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, WarnBorder, RoundedCornerShape(10.dp)),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Warning,
                    contentDescription = null,
                    tint = WarnText,
                    modifier = Modifier.size(18.dp).padding(top = 1.dp),
                )
                Text(
                    stringResource(Res.string.setup_password_warning),
                    fontSize = 13.sp,
                    color = WarnText,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        SetupTextField(
            value = state.serverUrl,
            onValueChange = viewModel::updateServerUrl,
            label = stringResource(Res.string.field_server_url),
            modifier = Modifier.testTag(AppTestTags.SetupBaseUrl),
        )

        PasswordField(
            value = state.masterPassword,
            onValueChange = viewModel::updateMasterPassword,
            label = stringResource(Res.string.setup_master_password_label),
            modifier = Modifier.testTag(AppTestTags.SetupMasterPassword),
            showStrength = true,
        )

        PasswordField(
            value = state.masterPasswordConfirm,
            onValueChange = viewModel::updateMasterPasswordConfirm,
            label = stringResource(Res.string.setup_master_password_confirm_label),
            modifier = Modifier.testTag(AppTestTags.SetupMasterPasswordConfirm),
            errorMessage = if (
                state.masterPasswordConfirm.isNotEmpty() &&
                state.masterPassword != state.masterPasswordConfirm
            ) stringResource(Res.string.setup_passwords_mismatch) else null,
        )

        Spacer(Modifier.height(4.dp))

        val canProceed = state.masterPassword.length >= 8 &&
                state.masterPassword == state.masterPasswordConfirm
        SetupPrimaryButton(
            text = stringResource(Res.string.action_next),
            onClick = viewModel::proceedFromPasswordStep,
            enabled = canProceed,
            modifier = Modifier.testTag(AppTestTags.SetupNext),
            showArrow = true,
        )
    }
}

// ── Step 2 — family members ───────────────────────────────────────────────────
@Composable
private fun StepMembers(state: SetupUiState, viewModel: SetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetupCard {
            Text(stringResource(Res.string.setup_members_title), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.setup_members_description),
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
            SetupTextField(
                value = state.familyName,
                onValueChange = viewModel::updateFamilyName,
                label = stringResource(Res.string.setup_family_name_label),
                placeholder = stringResource(Res.string.setup_family_name_placeholder),
                modifier = Modifier.testTag(AppTestTags.SetupFamilyName),
            )
        }

        state.members.forEachIndexed { index, member ->
            MemberCard(
                member = member,
                index = index,
                showRemove = state.members.size > 1,
                onNameChange = { viewModel.updateMemberName(index, it) },
                onRoleChange = { viewModel.updateMemberRole(index, it) },
                onAdminChange = { viewModel.updateMemberAdmin(index, it) },
                onRemove = { viewModel.removeMember(index) },
            )
        }

        // Add member
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = viewModel::addMember)
                .testTag(AppTestTags.SetupAddMember),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = null,
                    tint = TgBlue,
                    modifier = Modifier.size(16.dp),
                )
                Text(stringResource(Res.string.setup_add_member), fontSize = 14.sp, color = TgBlue)
            }
        }

        // Nav buttons
        val canCreate = state.familyName.isNotBlank() &&
                state.members.isNotEmpty() &&
                state.members.all { it.displayName.isNotBlank() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupSecondaryButton(
                text = stringResource(Res.string.action_back),
                onClick = { viewModel.goToStep(1) },
                modifier = Modifier.weight(1f).testTag(AppTestTags.SetupBack),
                showBackArrow = true,
            )
            SetupPrimaryButton(
                text = stringResource(Res.string.action_create),
                onClick = viewModel::submit,
                enabled = canCreate,
                modifier = Modifier.weight(1f).testTag(AppTestTags.SetupSubmit),
                showArrow = true,
            )
        }
    }
}

@Composable
private fun MemberCard(
    member: SetupMemberInputState,
    index: Int,
    showRemove: Boolean,
    onNameChange: (String) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onAdminChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        color = SurfaceBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.setup_member_number, index + 1), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                if (showRemove) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag(AppTestTags.SetupMemberRemovePrefix + index),
                    ) {
                        Text(stringResource(Res.string.action_remove), color = TgBlueDark, fontSize = 13.sp)
                    }
                }
            }

            SetupTextField(
                value = member.displayName,
                onValueChange = onNameChange,
                label = stringResource(Res.string.setup_member_name_label),
                placeholder = stringResource(Res.string.setup_member_name_placeholder),
                modifier = Modifier.testTag(AppTestTags.SetupMemberNamePrefix + index),
            )

            // Role picker
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(UserRole.PARENT, UserRole.CHILD).forEach { role ->
                    val selected = member.role == role
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) TgBlue else CardBg)
                            .border(
                                width = if (selected) 0.dp else 0.5.dp,
                                color = if (selected) Color.Transparent else CardBorder,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onRoleChange(role) }
                            .padding(vertical = 10.dp)
                            .testTag(AppTestTags.SetupMemberRolePrefix + index + "." + role.name.lowercase()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            role.localizedLabel(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) Color.White else TextSecondary,
                        )
                    }
                }
            }

            // Admin toggle — only for parents
            if (member.role == UserRole.PARENT) {
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAdminChange(!member.isAdmin) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Custom checkbox
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (member.isAdmin) TgBlue else CardBg)
                                .border(
                                    width = if (member.isAdmin) 0.dp else 1.5.dp,
                                    color = if (member.isAdmin) Color.Transparent else CardBorder,
                                    shape = RoundedCornerShape(4.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (member.isAdmin) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(Res.string.admin_member_admin_toggle_label), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                stringResource(Res.string.setup_admin_description),
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }

                        // Badge
                        Surface(
                            color = if (member.isAdmin) TgBlue else TgBlueTint,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = if (member.isAdmin) stringResource(Res.string.label_yes) else stringResource(Res.string.label_no),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (member.isAdmin) Color.White else TgBlueDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Step 3 — invite codes ─────────────────────────────────────────────────────
@Composable
private fun StepInvites(state: SetupUiState, viewModel: SetupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Success header
        SetupCard {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(TgBlueTint)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    tint = TgBlue,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.setup_done_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.setup_done_description),
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.generatedInvites.forEach { invite ->
            InviteCard(invite = invite, serverUrl = state.serverUrl)
        }

        SetupPrimaryButton(
            text = stringResource(Res.string.setup_enter_app),
            onClick = viewModel::finish,
            modifier = Modifier.testTag(AppTestTags.SetupFinish),
        )
    }
}

@Composable
private fun InviteCard(invite: SetupInviteSummary, serverUrl: String) {
    var showQr by remember { mutableStateOf(false) }
    if (showQr) {
        QrCodeDialog(
            inviteCode = invite.inviteCode,
            serverUrl = serverUrl,
            displayName = invite.displayName,
            onDismiss = { showQr = false },
        )
    }
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp)),
    ) {
        Column {
            // Top: name + role on left, code on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TgBlueTint)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Left column
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            invite.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = LinkBlue,
                        )
                        if (invite.isAdmin) {
                            Surface(
                                color = TgBlue,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    stringResource(Res.string.admin_member_admin_toggle_label),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        invite.role.localizedLabel(),
                        fontSize = 12.sp,
                        color = LinkBlue.copy(alpha = 0.8f),
                    )
                }

                // Right column — invite code
                Text(
                    invite.inviteCode,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = TgBlueDark,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                )
            }

            // Bottom: action buttons
            HorizontalDivider(color = TgBlueBorder, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InviteActionButton(
                    icon = AppIcons.Copy,
                    label = stringResource(Res.string.action_copy),
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO: clipboard copy invite.inviteCode */ },
                )
                InviteActionButton(
                    icon = AppIcons.QrCode,
                    label = stringResource(Res.string.action_qr_code),
                    modifier = Modifier.weight(1f),
                    onClick = { showQr = true },
                )
            }
        }
    }
}

@Composable
internal fun InviteActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = SurfaceBg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(14.dp),
            )
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        }
    }
}

// ── Shared small components ───────────────────────────────────────────────────

@Composable
private fun SetupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, CardBorder, RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    showStrength: Boolean = false,
    errorMessage: String? = null,
) {
    var visible by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, fontSize = 13.sp) },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Clear button — only when field has content
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(
                                imageVector = AppIcons.Clear,
                                contentDescription = stringResource(Res.string.action_clear),
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    // Eye toggle
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) AppIcons.EyeOff else AppIcons.Eye,
                            contentDescription = if (visible) stringResource(Res.string.action_hide) else stringResource(Res.string.action_show),
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            isError = errorMessage != null,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = CardBorder,
                focusedBorderColor = TgBlue,
                focusedLabelColor = TgBlue,
                errorBorderColor = ErrorRed,
            ),
        )

        // Password strength bar
        if (showStrength && value.isNotEmpty()) {
            val strength = passwordStrength(value)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardBorder),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(strength.fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(strength.color),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(strength.label, fontSize = 12.sp, color = strength.color)
        }

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(3.dp))
            Text(errorMessage, fontSize = 12.sp, color = ErrorRed)
        }
    }
}

private data class StrengthResult(val fraction: Float, val label: String, val color: Color)

@Composable
private fun passwordStrength(pw: String): StrengthResult {
    var score = 0
    if (pw.length >= 8)  score++
    if (pw.length >= 12) score++
    if (pw.any { it.isUpperCase() }) score++
    if (pw.any { it.isDigit() })     score++
    if (pw.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> StrengthResult(0.20f, stringResource(Res.string.password_strength_weak), StrengthWeak)
        score <= 3 -> StrengthResult(0.60f, stringResource(Res.string.password_strength_medium), StrengthMedium)
        else -> StrengthResult(1.00f, stringResource(Res.string.password_strength_strong), StrengthStrong)
    }
}

@Composable
private fun SetupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = TextSecondary) }) else null,
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = CardBorder,
            focusedBorderColor = TgBlue,
            focusedLabelColor = TgBlue,
        ),
    )
}

@Composable
private fun SetupPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArrow: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TgBlue,
            disabledContainerColor = CardBorder,
        ),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (showArrow) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = AppIcons.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SetupSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackArrow: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = TextSecondary,
        ),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, CardBorder),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
    ) {
        if (showBackArrow) {
            Icon(
                imageVector = AppIcons.ArrowLeft,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 15.sp)
    }
}
