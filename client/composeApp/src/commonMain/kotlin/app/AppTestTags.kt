package app

object AppTestTags {
    const val OnboardingBaseUrl = "onboarding.baseUrl"
    const val OnboardingInviteCode = "onboarding.inviteCode"
    const val OnboardingRegisterTab = "onboarding.auth.register"
    const val OnboardingLoginTab = "onboarding.auth.login"
    const val OnboardingSubmit = "onboarding.submit"

    const val TopBarSettings = "topbar.settings"
    const val TopBarRefresh = "topbar.refresh"
    const val SettingsBaseUrl = "settings.baseUrl"
    const val SettingsSaveBaseUrl = "settings.baseUrl.save"
    const val SettingsLogout = "settings.logout"

    const val ChatInput = "chat.input"
    const val ChatSend = "chat.send"

    const val ContactRowPrefix = "contact.row."
}

fun contactRowTag(contactId: Long): String = AppTestTags.ContactRowPrefix + contactId
