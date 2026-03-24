package app

sealed class AppException(message: String) : RuntimeException(message) {
    class Network(message: String) : AppException(message)
    class Unauthorized(message: String) : AppException(message)
    class Validation(message: String) : AppException(message)
    class Conflict(message: String) : AppException(message)
    class RateLimited(message: String) : AppException(message)
    class Server(message: String) : AppException(message)
}
