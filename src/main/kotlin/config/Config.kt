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
    val env: String = "staging",
    val port: Int = 8080,
    val enableTestAcks: Boolean = false
) {
    override fun toString(): String {
        return "Config(" +
            "telegramBotToken=***MASKED***, " +
            "telegramAdminId=***MASKED***, " +
            "elevenLabsApiKey=***MASKED***, " +
            "webhookPath=***MASKED***, " +
            "webhookSecret=***MASKED***, " +
            "adminHttpToken=***MASKED***, " +
            "env='$env', " +
            "port=$port, " +
            "enableTestAcks=$enableTestAcks" +
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
        val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
        val enableTestAcks = parseBooleanEnv("ENABLE_TEST_ACKS", false)
        
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
        
        // Validate webhook path length and character set
        if (webhookPath != null) {
            if (webhookPath.length < 32) {
                errors.add("WEBHOOK_PATH should be at least 32 characters for security")
            }
            // Validate that webhook path contains only URL-safe characters
            val urlSafeRegex = Regex("^[A-Za-z0-9_-]+$")
            if (!webhookPath.matches(urlSafeRegex)) {
                errors.add("WEBHOOK_PATH must contain only alphanumeric characters, underscores, and hyphens")
            }
        }
        
        // Validate webhook secret length
        if (webhookSecret != null && webhookSecret.length < 64) {
            errors.add("WEBHOOK_SECRET should be at least 64 characters for security")
        }
        
        // Validate admin token length
        if (adminHttpToken != null && adminHttpToken.length < 32) {
            errors.add("ADMIN_HTTP_TOKEN should be at least 32 characters for security")
        }
        
        // Validate ENV value
        if (env !in listOf("staging", "prod")) {
            errors.add("ENV must be either 'staging' or 'prod', got: '$env'")
        }
        
        // Validate PORT range
        if (port !in 1..65535) {
            errors.add("PORT must be between 1 and 65535, got: $port")
        }
        
        // Warn if PORT is not 8080 in production (Caddy expects 8080)
        if (env == "prod" && port != 8080) {
            System.err.println("WARNING: PORT is set to $port in production environment, but Caddy expects 8080. This may break internal routing.")
        }
        
        // Production environment security checks
        if (env == "prod") {
            // Fail fast if webhook secret is missing in production
            if (webhookSecret.isNullOrBlank()) {
                errors.add("WEBHOOK_SECRET is required and cannot be empty in production environment")
            }
            
            // Fail fast if test acknowledgments are enabled in production
            if (enableTestAcks) {
                errors.add("ENABLE_TEST_ACKS must be disabled (false) in production environment for security")
            }
        }
        
        if (errors.isNotEmpty()) {
            val errorMessage = buildString {
                appendLine("Configuration errors:")
                errors.forEach { appendLine("  - $it") }
                appendLine("\nPlease check your environment variables and .env file.")
                appendLine("See .env.example for required variables.")
            }
            throw IllegalStateException(errorMessage)
        }
        
        return Config(
            telegramBotToken = telegramBotToken!!,
            telegramAdminId = telegramAdminId,
            elevenLabsApiKey = elevenLabsApiKey!!,
            webhookPath = webhookPath!!,
            webhookSecret = webhookSecret!!,
            adminHttpToken = adminHttpToken!!,
            env = env,
            port = port,
            enableTestAcks = enableTestAcks
        )
    }
    
    private fun getEnvVar(name: String, errors: MutableList<String>): String? {
        return System.getenv(name) ?: run {
            errors.add("$name is required but not set")
            null
        }
    }
    
    private fun parseBooleanEnv(name: String, default: Boolean): Boolean {
        val value = System.getenv(name)?.trim()?.lowercase() ?: return default
        return when (value) {
            "1", "true", "yes", "on", "y", "t" -> true
            "0", "false", "no", "off", "n", "f" -> false
            else -> {
                System.err.println("WARNING: Invalid boolean value '$value' for $name, using default: $default")
                default
            }
        }
    }
}