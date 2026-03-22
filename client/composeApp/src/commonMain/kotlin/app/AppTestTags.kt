package app

object AppTestTags {
    const val OnboardingBaseUrl = "onboarding.baseUrl"
    const val OnboardingInviteCode = "onboarding.inviteCode"
    const val OnboardingSubmit = "onboarding.submit"
    const val SetupBaseUrl = "setup.baseUrl"
    const val SetupMasterPassword = "setup.masterPassword"
    const val SetupMasterPasswordConfirm = "setup.masterPasswordConfirm"
    const val SetupFamilyName = "setup.familyName"
    const val SetupNext = "setup.next"
    const val SetupBack = "setup.back"
    const val SetupSubmit = "setup.submit"
    const val SetupFinish = "setup.finish"
    const val SetupAddMember = "setup.member.add"
    const val SetupMemberNamePrefix = "setup.member.name."
    const val SetupMemberRolePrefix = "setup.member.role."
    const val SetupMemberRemovePrefix = "setup.member.remove."

    const val TopBarSettings = "topbar.settings"
    const val TopBarRefresh = "topbar.refresh"
    const val SettingsBaseUrl = "settings.baseUrl"
    const val SettingsSaveBaseUrl = "settings.baseUrl.save"
    const val SettingsOpenAdmin = "settings.admin.open"
    const val SettingsLogout = "settings.logout"

    const val AdminPassword = "admin.password"
    const val AdminUnlock = "admin.unlock"
    const val AdminMemberName = "admin.member.name"
    const val AdminMemberRolePrefix = "admin.member.role."
    const val AdminMemberAdmin = "admin.member.admin"
    const val AdminMemberCreate = "admin.member.create"
    const val AdminMemberRemovePrefix = "admin.member.remove."

    const val ChatInput = "chat.input"
    const val ChatSend = "chat.send"

    const val ContactRowPrefix = "contact.row."
}

fun contactRowTag(contactId: Long): String = AppTestTags.ContactRowPrefix + contactId
