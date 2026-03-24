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
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 10_000
    }
    install(ContentNegotiation) {
        json(clientJson)
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}

class ClientApp private constructor(
    private val koinHandle: org.koin.core.KoinApplication,
) {
    val viewModel: AppViewModel = koinHandle.koin.get()

    fun close() {
        viewModel.close()
        koinHandle.close()
    }

    companion object {
        fun create(platformServices: PlatformServices): ClientApp {
            val app = koinApplication {
                modules(commonClientModule(platformServices))
            }
            return ClientApp(app)
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
    single { LoginUseCase(get(), get()) }
    single { LoadSetupStatusUseCase(get()) }
    single { BootstrapSystemUseCase(get()) }
    single { VerifyAdminAccessUseCase(get()) }
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
            bootstrapSystem = get(),
            verifyAdminAccess = get(),
            createMember = get(),
            removeMember = get(),
            loadContacts = get(),
            sendTextMessageUseCase = get(),
            sendQuickActionUseCase = get(),
            shareLocationUseCase = get(),
        )
    }
}
