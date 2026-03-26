package app.backend.di

import app.backend.config.AppConfig
import app.backend.db.DatabaseFactory
import app.backend.repository.AdminRepository
import app.backend.repository.AuthRepository
import app.backend.repository.ClientLogRepository
import app.backend.repository.DeviceRepository
import app.backend.repository.MessageRepository
import app.backend.repository.PresenceRepository
import app.backend.repository.ProfileRepository
import app.backend.repository.SetupRepository
import app.backend.repository.impl.ExposedAdminRepository
import app.backend.repository.impl.ExposedAuthRepository
import app.backend.repository.impl.ExposedClientLogRepository
import app.backend.repository.impl.ExposedDeviceRepository
import app.backend.repository.impl.ExposedMessageRepository
import app.backend.repository.impl.ExposedPresenceRepository
import app.backend.repository.impl.ExposedProfileRepository
import app.backend.repository.impl.ExposedSetupRepository
import app.backend.service.AdminService
import app.backend.service.AuthService
import app.backend.service.ClientLogService
import app.backend.service.DeviceService
import app.backend.service.FcmPushService
import app.backend.service.MessageService
import app.backend.service.PresenceService
import app.backend.service.ProfileService
import app.backend.service.RateLimitService
import app.backend.service.SetupService
import app.backend.service.SyncNotifier
import app.backend.service.TokenService
import org.koin.dsl.module

fun backendModule(appConfig: AppConfig) = module {
    single { appConfig }
    single { appConfig.auth }
    single { appConfig.rateLimit }
    single { appConfig.firebase }
    single { FcmPushService(get()) }
    single { DatabaseFactory(appConfig.database) }
    single { TokenService(get()) }
    single { RateLimitService(get()) }

    single<AdminRepository> { ExposedAdminRepository() }
    single<AuthRepository> { ExposedAuthRepository() }
    single<ClientLogRepository> { ExposedClientLogRepository() }
    single<ProfileRepository> { ExposedProfileRepository() }
    single<MessageRepository> { ExposedMessageRepository() }
    single<PresenceRepository> { ExposedPresenceRepository() }
    single<DeviceRepository> { ExposedDeviceRepository() }
    single<SetupRepository> { ExposedSetupRepository() }

    single { AdminService(get()) }
    single { AuthService(get(), get(), get()) }
    single { ClientLogService(get()) }
    single { ProfileService(get()) }
    single { SyncNotifier() }
    single { MessageService(get(), get(), get(), get(), get()) }
    single { PresenceService(get()) }
    single { DeviceService(get()) }
    single { SetupService(get()) }
}
