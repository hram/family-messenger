package app.di

import app.config.AppConfig
import app.db.DatabaseFactory
import app.repository.AdminRepository
import app.repository.AuthRepository
import app.repository.DeviceRepository
import app.repository.ExposedAdminRepository
import app.repository.ExposedAuthRepository
import app.repository.ExposedDeviceRepository
import app.repository.ExposedMessageRepository
import app.repository.ExposedPresenceRepository
import app.repository.ExposedProfileRepository
import app.repository.ExposedSetupRepository
import app.repository.MessageRepository
import app.repository.PresenceRepository
import app.repository.ProfileRepository
import app.repository.SetupRepository
import app.service.AdminService
import app.service.AuthService
import app.service.DeviceService
import app.service.MessageService
import app.service.PresenceService
import app.service.ProfileService
import app.service.RateLimitService
import app.service.SetupService
import app.service.TokenService
import org.koin.dsl.module

fun backendModule(appConfig: AppConfig) = module {
    single { appConfig }
    single { appConfig.auth }
    single { appConfig.rateLimit }
    single { DatabaseFactory(appConfig.database) }
    single { TokenService(get()) }
    single { RateLimitService(get()) }

    single<AdminRepository> { ExposedAdminRepository() }
    single<AuthRepository> { ExposedAuthRepository() }
    single<ProfileRepository> { ExposedProfileRepository() }
    single<MessageRepository> { ExposedMessageRepository() }
    single<PresenceRepository> { ExposedPresenceRepository() }
    single<DeviceRepository> { ExposedDeviceRepository() }
    single<SetupRepository> { ExposedSetupRepository() }

    single { AdminService(get()) }
    single { AuthService(get(), get(), get()) }
    single { ProfileService(get()) }
    single { MessageService(get()) }
    single { PresenceService(get()) }
    single { DeviceService(get()) }
    single { SetupService(get()) }
}
