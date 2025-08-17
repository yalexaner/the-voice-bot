package config

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val telegramBotToken: String,
    val telegramAdminId: Long,
    val elevenLabsApiKey: String,
    val webhookPath: String,
    val webhookSecret: String,
    val adminHttpToken: String,
    val env: String = "staging"
) {
    override fun toString(): String {
        return "Config(" +
            "telegramBotToken=***MASKED***, " +
            "telegramAdminId=$telegramAdminId, " +
            "elevenLabsApiKey=***MASKED***, " +
            "webhookPath=***MASKED***, " +
            "webhookSecret=***MASKED***, " +
            "adminHttpToken=***MASKED***, " +
            "env='$env'" +
            ")"
    }
}

object ConfigLoader {
    fun load(): Config {
        val errors = mutableListOf<String>()
        
        val telegramBotToken = getEnvVar("TELEGRAM_BOT_TOKEN", errors)
        val telegramAdminIdStr = getEnvVar("TELEGRAM_ADMIN_ID", errors)
        val elevenLabsApiKey = getEnvVar("ELEVENLABS_API_KEY", errors)
        val webhookPath = getEnvVar("WEBHOOK_PATH", errors)
        val webhookSecret = getEnvVar("WEBHOOK_SECRET", errors)
        val adminHttpToken = getEnvVar("ADMIN_HTTP_TOKEN", errors)
        
        val env = System.getenv("ENV") ?: "staging"
        
        // Validate admin ID
        val telegramAdminId = try {
            telegramAdminIdStr?.toLong() ?: run {
                errors.add("TELEGRAM_ADMIN_ID is required")
                0L
            }
        } catch (e: NumberFormatException) {
            errors.add("TELEGRAM_ADMIN_ID must be a valid long integer, got: $telegramAdminIdStr")
            0L
        }
        
        // Validate webhook path length
        if (webhookPath != null && webhookPath.length < 32) {
            errors.add("WEBHOOK_PATH should be at least 32 characters for security")
        }
        
        // Validate webhook secret length
        if (webhookSecret != null && webhookSecret.length < 64) {
            errors.add("WEBHOOK_SECRET should be at least 64 characters for security")
        }
        
        if (errors.isNotEmpty()) {
            System.err.println("Configuration errors:")
            errors.forEach { System.err.println("  - $it") }
            System.err.println("\nPlease check your environment variables and .env file.")
            System.err.println("See .env.example for required variables.")
            kotlin.system.exitProcess(1)
        }
        
        return Config(
            telegramBotToken = telegramBotToken!!,
            telegramAdminId = telegramAdminId,
            elevenLabsApiKey = elevenLabsApiKey!!,
            webhookPath = webhookPath!!,
            webhookSecret = webhookSecret!!,
            adminHttpToken = adminHttpToken!!,
            env = env
        )
    }
    
    private fun getEnvVar(name: String, errors: MutableList<String>): String? {
        return System.getenv(name) ?: run {
            errors.add("$name is required but not set")
            null
        }
    }
}