package app

import app.network.ApiExecutor
import app.network.FamilyMessengerApiClient
import app.repository.AdminRepository
import app.repository.ContactsRepository
import app.repository.DeviceRepository
import app.repository.MessagesRepository
import app.repository.PresenceRepository
import app.repository.SessionRepository
import app.repository.SetupRepository
import app.storage.ClientSettingsRepository
import app.storage.LocalDatabase
import app.storage.SessionStore
import app.usecase.BootstrapSystemUseCase
import app.usecase.CreateMemberUseCase
import app.usecase.LoadContactsUseCase
import app.usecase.LoadSetupStatusUseCase
import app.usecase.LoginUseCase
import app.usecase.RemoveMemberUseCase
import app.usecase.SendQuickActionUseCase
import app.usecase.SendTextMessageUseCase
import app.usecase.ShareLocationUseCase
import app.usecase.VerifyAdminAccessUseCase
import app.usecase.VerifyMasterPasswordUseCase
import com.familymessenger.contract.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal val clientJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun configuredHttpClient(base: HttpClient): HttpClient = base.config {
    expectSuccess = false
    install(ContentNegotiation) {
        json(clientJson)
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}

class ClientApp private constructor(
    private val koinHandle: org.koin.core.KoinApplication,
    val setupViewModel: SetupViewModel?,
) {
    val viewModel: AppViewModel = koinHandle.koin.get()

    fun close() {
        viewModel.close()
        koinHandle.close()
    }

    companion object {
        fun create(platformServices: PlatformServices): ClientApp {
            val isWeb = platformServices.platformInfo.type == PlatformType.WEB
            val modules = buildList {
                add(commonClientModule(platformServices))
                if (isWeb) add(webSetupModule())
            }
            val app = koinApplication { modules(modules) }
            val setupViewModel = if (isWeb) {
                val bootstrapUseCase = app.koin.get<BootstrapSystemUseCase>()
                val settingsRepo = app.koin.get<app.storage.ClientSettingsRepository>()
                val appViewModel = app.koin.get<AppViewModel>()
                SetupViewModel(
                    settingsRepository = settingsRepo,
                    bootstrapSystem = bootstrapUseCase,
                    onFinished = appViewModel::onSetupComplete,
                )
            } else null
            return ClientApp(app, setupViewModel)
        }
    }
}

private fun commonClientModule(platformServices: PlatformServices): Module = module {
    single { clientJson }
    single { platformServices.platformInfo }
    single { configuredHttpClient(platformServices.httpClient) }
    single { platformServices.settingsStore }
    single(qualifier = named("secure")) { platformServices.secureStore }
    single { platformServices.geolocationService }
    single { platformServices.notificationService }
    single { LocalDatabase(get(), get()) }
    single { SessionStore(get(named("secure")), get()) }
    single { ClientSettingsRepository(get(), get()) }
    single { ApiExecutor(get()) }
    single { FamilyMessengerApiClient(get(), get(), get(), get()) }
    single { SessionRepository(get(), get(), get(), get()) }
    single { SetupRepository(get()) }
    single { AdminRepository(get()) }
    single { ContactsRepository(get(), get()) }
    single { MessagesRepository(get(), get(), get()) }
    single { PresenceRepository(get(), get()) }
    single { DeviceRepository(get()) }
    single { SyncEngine(get(), get(), get(), get(), get(), get(), get()) }
    single { LoginUseCase(get()) }
    single { LoadSetupStatusUseCase(get()) }
    single { VerifyAdminAccessUseCase(get()) }
    single { VerifyMasterPasswordUseCase(get()) }
    single { CreateMemberUseCase(get()) }
    single { RemoveMemberUseCase(get()) }
    single { LoadContactsUseCase(get()) }
    single { SendTextMessageUseCase(get()) }
    single { SendQuickActionUseCase(get()) }
    single { ShareLocationUseCase(get(), get()) }
    single {
        AppViewModel(
            platformInfo = get(),
            localDatabase = get(),
            settingsRepository = get(),
            sessionStore = get(),
            contactsRepository = get(),
            messagesRepository = get(),
            sessionRepository = get(),
            syncEngine = get(),
            login = get(),
            loadSetupStatus = get(),
            verifyAdminAccess = get(),
            verifyMasterPassword = get(),
            createMember = get(),
            removeMember = get(),
            loadContacts = get(),
            sendTextMessageUseCase = get(),
            sendQuickActionUseCase = get(),
            shareLocationUseCase = get(),
        )
    }
}

private fun webSetupModule() = org.koin.dsl.module {
    single { BootstrapSystemUseCase(get()) }
}
