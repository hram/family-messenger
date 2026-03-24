package app.ui

import app.AppViewModel
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
import com.familymessenger.contract.SetupInviteSummary
import com.familymessenger.contract.UserRole

// ── Palette (shared with App.kt) ─────────────────────────────────────────────
private val SetupBlue      = Color(0xFF2AABEE)
private val SetupBlueDark  = Color(0xFF1A8DD1)
private val SetupBlueTint  = Color(0xFFE8F4FD)
private val SetupBlueBorder= Color(0xFFB5D4F4)
private val SetupPageBg    = Color(0xFFF0F2F5)
private val WarnBg         = Color(0xFFFFF8E8)
private val WarnBorder     = Color(0xFFF0C060)
private val WarnText       = Color(0xFF633806)
private val TextPrimary    = Color(0xFF000000)
private val TextSecondary  = Color(0xFF8A8A8A)
private val CardBorder     = Color(0xFFE8E8E8)
private val SurfaceBg      = Color(0xFFF5F5F5)
private val ErrorRed       = Color(0xFFE24B4A)

// ── Root ──────────────────────────────────────────────────────────────────────
@Composable
internal fun SetupScreen(state: AppUiState, viewModel: AppViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SetupPageBg),
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
            StepProgressBar(currentStep = state.setup.step)

            when (state.setup.step) {
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
                            isDone || isActive -> SetupBlue
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
                        .background(if (step < currentStep) SetupBlue else CardBorder),
                )
            }
        }
    }
}

// ── Step 1 — master password ──────────────────────────────────────────────────
@Composable
private fun StepPassword(state: AppUiState, viewModel: AppViewModel) {
    SetupCard {
        Text("Защита администратора", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Придумайте пароль для панели администрирования. Через неё можно добавлять и удалять участников.",
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
                    "Если забудете этот пароль — доступ к панели администрирования будет потерян. Запишите его в надёжном месте.",
                    fontSize = 13.sp,
                    color = WarnText,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        SetupTextField(
            value = state.onboarding.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            label = "Адрес сервера",
            modifier = Modifier.testTag(AppTestTags.SetupBaseUrl),
        )

        PasswordField(
            value = state.setup.masterPassword,
            onValueChange = viewModel::updateSetupMasterPassword,
            label = "Пароль администратора",
            modifier = Modifier.testTag(AppTestTags.SetupMasterPassword),
            showStrength = true,
        )

        PasswordField(
            value = state.setup.masterPasswordConfirm,
            onValueChange = viewModel::updateSetupMasterPasswordConfirm,
            label = "Повторите пароль",
            modifier = Modifier.testTag(AppTestTags.SetupMasterPasswordConfirm),
            errorMessage = if (
                state.setup.masterPasswordConfirm.isNotEmpty() &&
                state.setup.masterPassword != state.setup.masterPasswordConfirm
            ) "Пароли не совпадают" else null,
        )

        Spacer(Modifier.height(4.dp))

        val canProceed = state.setup.masterPassword.length >= 8 &&
                state.setup.masterPassword == state.setup.masterPasswordConfirm
        SetupPrimaryButton(
            text = "Далее",
            onClick = viewModel::proceedFromSetupPasswordStep,
            enabled = canProceed,
            modifier = Modifier.testTag(AppTestTags.SetupNext),
            showArrow = true,
        )
    }
}

// ── Step 2 — family members ───────────────────────────────────────────────────
@Composable
private fun StepMembers(state: AppUiState, viewModel: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetupCard {
            Text("Участники семьи", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Добавьте всех, кто будет пользоваться чатом.",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
            SetupTextField(
                value = state.setup.familyName,
                onValueChange = viewModel::updateSetupFamilyName,
                label = "Название семьи",
                placeholder = "Например: Семья Ивановых",
                modifier = Modifier.testTag(AppTestTags.SetupFamilyName),
            )
        }

        state.setup.members.forEachIndexed { index, member ->
            MemberCard(
                member = member,
                index = index,
                showRemove = state.setup.members.size > 1,
                onNameChange = { viewModel.updateSetupMemberName(index, it) },
                onRoleChange = { viewModel.updateSetupMemberRole(index, it) },
                onAdminChange = { viewModel.updateSetupMemberAdmin(index, it) },
                onRemove = { viewModel.removeSetupMember(index) },
            )
        }

        // Add member
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = viewModel::addSetupMember)
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
                    tint = SetupBlue,
                    modifier = Modifier.size(16.dp),
                )
                Text("Добавить участника", fontSize = 14.sp, color = SetupBlue)
            }
        }

        // Nav buttons
        val canCreate = state.setup.familyName.isNotBlank() &&
                state.setup.members.isNotEmpty() &&
                state.setup.members.all { it.displayName.isNotBlank() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupSecondaryButton(
                text = "Назад",
                onClick = { viewModel.goToSetupStep(1) },
                modifier = Modifier.weight(1f).testTag(AppTestTags.SetupBack),
                showBackArrow = true,
            )
            SetupPrimaryButton(
                text = "Создать",
                onClick = viewModel::submitSetup,
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
                Text("Участник ${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                if (showRemove) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag(AppTestTags.SetupMemberRemovePrefix + index),
                    ) {
                        Text("Удалить", color = SetupBlueDark, fontSize = 13.sp)
                    }
                }
            }

            SetupTextField(
                value = member.displayName,
                onValueChange = onNameChange,
                label = "Имя",
                placeholder = "Например: Мама",
                modifier = Modifier.testTag(AppTestTags.SetupMemberNamePrefix + index),
            )

            // Role picker
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(UserRole.PARENT to "Родитель", UserRole.CHILD to "Ребёнок").forEach { (role, label) ->
                    val selected = member.role == role
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) SetupBlue else Color.White)
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
                            label,
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
                    color = Color.White,
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
                                .background(if (member.isAdmin) SetupBlue else Color.White)
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
                            Text("Администратор", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(
                                "Доступ к панели управления участниками",
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }

                        // Badge
                        Surface(
                            color = if (member.isAdmin) SetupBlue else SetupBlueTint,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = if (member.isAdmin) "Да" else "Нет",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (member.isAdmin) Color.White else SetupBlueDark,
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
private fun StepInvites(state: AppUiState, viewModel: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Success header
        SetupCard {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SetupBlueTint)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    tint = SetupBlue,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Готово!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Отправьте каждому участнику его код или покажите QR — они отсканируют и сразу войдут.",
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.setup.generatedInvites.forEach { invite ->
            InviteCard(invite = invite, serverUrl = state.onboarding.baseUrl)
        }

        SetupPrimaryButton(
            text = "Войти в приложение",
            onClick = viewModel::finishSetup,
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
        color = Color.White,
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
                    .background(SetupBlueTint)
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
                            color = Color(0xFF185FA5),
                        )
                        if (invite.isAdmin) {
                            Surface(
                                color = SetupBlue,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    "Администратор",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        invite.role.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                        fontSize = 12.sp,
                        color = Color(0xFF185FA5).copy(alpha = 0.8f),
                    )
                }

                // Right column — invite code
                Text(
                    invite.inviteCode,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = SetupBlueDark,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                )
            }

            // Bottom: action buttons
            HorizontalDivider(color = SetupBlueBorder, thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InviteActionButton(
                    icon = AppIcons.Copy,
                    label = "Скопировать",
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO: clipboard copy invite.inviteCode */ },
                )
                InviteActionButton(
                    icon = AppIcons.QrCode,
                    label = "QR-код",
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
        color = Color.White,
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
                                contentDescription = "Очистить",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    // Eye toggle
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) AppIcons.EyeOff else AppIcons.Eye,
                            contentDescription = if (visible) "Скрыть" else "Показать",
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
                focusedBorderColor = SetupBlue,
                focusedLabelColor = SetupBlue,
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

private fun passwordStrength(pw: String): StrengthResult {
    var score = 0
    if (pw.length >= 8)  score++
    if (pw.length >= 12) score++
    if (pw.any { it.isUpperCase() }) score++
    if (pw.any { it.isDigit() })     score++
    if (pw.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> StrengthResult(0.20f, "Слабый пароль",   Color(0xFFE24B4A))
        score <= 3 -> StrengthResult(0.60f, "Средний пароль",  Color(0xFFEF9F27))
        else       -> StrengthResult(1.00f, "Надёжный пароль", Color(0xFF639922))
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
            focusedBorderColor = SetupBlue,
            focusedLabelColor = SetupBlue,
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
            containerColor = SetupBlue,
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
