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
    /**
     * Returns a string representation of this Config with sensitive fields redacted.
     *
     * Tokens, IDs and secrets are replaced with `***MASKED***` to avoid leaking credentials;
     * non-sensitive fields (`env`, `port`, `enableTestAcks`) are shown.
     *
     * @return A redacted string representation suitable for logging or diagnostics.
     */
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
    /**
     * Loads configuration from environment variables, validates them, and returns a populated [Config].
     *
     * Reads required variables (TELEGRAM_BOT_TOKEN, TELEGRAM_ADMIN_ID, ELEVENLABS_API_KEY,
     * WEBHOOK_PATH, WEBHOOK_SECRET, ADMIN_HTTP_TOKEN) and optional variables (ENV, PORT, ENABLE_TEST_ACKS).
     * Performs validation of TELEGRAM_ADMIN_ID (must be a long), minimum lengths for WEBHOOK_PATH and WEBHOOK_SECRET,
     * allowed values for ENV ("staging" or "prod"), and PORT range (1..65535). If ENV is "prod" and PORT != 8080,
     * a warning is emitted to standard error.
     *
     * @return a validated [Config] instance constructed from the environment.
     * @throws IllegalStateException if any required variables are missing or any validation checks fail;
     *         the exception message lists all configuration errors.
     */
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
        val enableTestAcks = System.getenv("ENABLE_TEST_ACKS")?.toBoolean() ?: false
        
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
}