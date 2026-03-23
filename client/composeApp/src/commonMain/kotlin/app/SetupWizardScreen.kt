package app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familymessenger.contract.UserRole

private val SetupBlue = Color(0xFF2AABEE)
private val SetupBlueDark = Color(0xFF1A8DD1)
private val SetupBlueTint = Color(0xFFE8F4FD)
private val SetupTextPrimary = Color(0xFF000000)
private val SetupTextSecondary = Color(0xFF8A8A8A)

@Composable
internal fun SetupScreen(state: AppUiState, viewModel: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SetupBlueDark)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                "First Launch Setup",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SetupTextField(
                value = state.onboarding.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = "Server Base URL",
                modifier = Modifier.testTag(AppTestTags.SetupBaseUrl),
            )

            when (state.setup.step) {
                1 -> SetupPasswordStep(state, viewModel)
                2 -> SetupMembersStep(state, viewModel)
                else -> SetupSummaryStep(state, viewModel)
            }
        }
    }
}

@Composable
private fun SetupPasswordStep(state: AppUiState, viewModel: AppViewModel) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Step 1 of 2", fontSize = 13.sp, color = SetupTextSecondary)
            Text("Create master password", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            PasswordSetupField(
                value = state.setup.masterPassword,
                onValueChange = viewModel::updateSetupMasterPassword,
                label = "Master Password",
                modifier = Modifier.testTag(AppTestTags.SetupMasterPassword),
            )
            PasswordSetupField(
                value = state.setup.masterPasswordConfirm,
                onValueChange = viewModel::updateSetupMasterPasswordConfirm,
                label = "Confirm Password",
                modifier = Modifier.testTag(AppTestTags.SetupMasterPasswordConfirm),
            )
            Button(
                onClick = viewModel::proceedFromSetupPasswordStep,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag(AppTestTags.SetupNext),
                colors = ButtonDefaults.buttonColors(containerColor = SetupBlue),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
internal fun PasswordSetupField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(AppIcons.Clear, contentDescription = "Clear", tint = SetupTextSecondary)
                    }
                }
                IconButton(onClick = { isVisible = !isVisible }) {
                    Icon(
                        imageVector = if (isVisible) AppIcons.EyeOff else AppIcons.Eye,
                        contentDescription = if (isVisible) "Hide password" else "Show password",
                        tint = SetupTextSecondary,
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFE0E0E0),
            focusedBorderColor = SetupBlue,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun SetupMembersStep(state: AppUiState, viewModel: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Step 2 of 2", fontSize = 13.sp, color = SetupTextSecondary)
                Text("Family and invite codes", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                SetupTextField(
                    value = state.setup.familyName,
                    onValueChange = viewModel::updateSetupFamilyName,
                    label = "Family Name",
                    modifier = Modifier.testTag(AppTestTags.SetupFamilyName),
                )
            }
        }

        state.setup.members.forEachIndexed { index, member ->
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Member ${index + 1}", fontWeight = FontWeight.Medium)
                        if (state.setup.members.size > 1) {
                            TextButton(
                                onClick = { viewModel.removeSetupMember(index) },
                                modifier = Modifier.testTag(AppTestTags.SetupMemberRemovePrefix + index),
                            ) {
                                Text("Remove", color = SetupBlueDark)
                            }
                        }
                    }
                    SetupTextField(
                        value = member.displayName,
                        onValueChange = { viewModel.updateSetupMemberName(index, it) },
                        label = "Display Name",
                        modifier = Modifier.testTag(AppTestTags.SetupMemberNamePrefix + index),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(UserRole.PARENT to "Parent", UserRole.CHILD to "Child").forEach { (role, label) ->
                            val selected = member.role == role
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) SetupBlue else SetupBlueTint)
                                    .clickable { viewModel.updateSetupMemberRole(index, role) }
                                    .padding(vertical = 12.dp)
                                    .testTag(AppTestTags.SetupMemberRolePrefix + index + "." + role.name.lowercase()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (selected) Color.White else SetupBlueDark, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (member.role == UserRole.PARENT) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Administrator", fontWeight = FontWeight.Medium, color = SetupTextPrimary)
                                Text("Can open admin section and manage children", fontSize = 12.sp, color = SetupTextSecondary)
                            }
                            Switch(
                                checked = member.isAdmin,
                                onCheckedChange = { viewModel.updateSetupMemberAdmin(index, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SetupBlue,
                                ),
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = viewModel::addSetupMember, modifier = Modifier.testTag(AppTestTags.SetupAddMember)) {
                Text("Add Member", color = SetupBlueDark)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.goToSetupStep(1) },
                modifier = Modifier.weight(1f).height(48.dp).testTag(AppTestTags.SetupBack),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB7BEC8)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Back")
            }
            Button(
                onClick = viewModel::submitSetup,
                modifier = Modifier.weight(1f).height(48.dp).testTag(AppTestTags.SetupSubmit),
                colors = ButtonDefaults.buttonColors(containerColor = SetupBlue),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Initialize")
            }
        }
    }
}

@Composable
private fun SetupSummaryStep(state: AppUiState, viewModel: AppViewModel) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Setup complete", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("Share these invite codes with family members.", color = SetupTextSecondary)
            state.setup.generatedInvites.forEach { invite ->
                Surface(color = SetupBlueTint, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(invite.displayName, fontWeight = FontWeight.Medium)
                        Text(
                            buildString {
                                append(invite.role.name.lowercase())
                                if (invite.isAdmin) append(" · administrator")
                            },
                            color = SetupTextSecondary,
                            fontSize = 13.sp,
                        )
                        Text(invite.inviteCode, color = SetupBlueDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Button(
                onClick = viewModel::finishSetup,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag(AppTestTags.SetupFinish),
                colors = ButtonDefaults.buttonColors(containerColor = SetupBlue),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Go To Login")
            }
        }
    }
}

@Composable
private fun SetupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFDDDDDD),
            focusedBorderColor = SetupBlue,
            focusedLabelColor = SetupBlue,
        ),
    )
}
