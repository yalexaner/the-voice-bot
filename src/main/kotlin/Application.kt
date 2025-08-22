import bot.TelegramClient
import bot.WebhookHandler
import config.ConfigLoader
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.system.measureTimeMillis

private val logger = LoggerFactory.getLogger("Application")

@Serializable
data class HealthResponse(
    val status: String,
    val uptimeSeconds: Long
)

@Serializable
data class MetricsResponse(
    val totalRequests: Long = 0,
    val totalFailures: Long = 0,
    val averageTranscriptionTimeMs: Double = 0.0,
    val uptimeSeconds: Long
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)

/**
 * Application entry point: loads configuration, initializes services, and starts the HTTP server.
 *
 * Attempts to load configuration via ConfigLoader.load() and exits the process with code 1 if loading
 * fails with an IllegalStateException. Initializes the Telegram client and configures webhook handling.
 * Starts an embedded Ktor Netty server bound to 0.0.0.0 on the configured port (blocking).
 *
 * The server installs JSON content negotiation (pretty print, lenient, ignore unknown keys), forwarded
 * header handling, and status page handlers for unhandled exceptions (returns 500) and not-found (404).
 *
 * Routes:
 * - GET /health: Admin-only (requires X-Admin-Token header matching config.adminHttpToken). Returns
 *   HealthResponse with uptime in seconds.
 * - GET /metrics: Admin-only (same auth). Returns MetricsResponse with uptime; other metrics are placeholders.
 * - POST /webhook/{path}: Validates the path against config.webhookPath and the X-Telegram-Bot-Api-Secret-Token
 *   header against config.webhookSecret. On successful validation delegates handling to WebhookHandler.handle(call).
 *
 * Side effects:
 * - May call kotlin.system.exitProcess(1) on configuration load failure.
 * - Initializes TelegramClient and WebhookHandler.
 * - Starts a blocking HTTP server.
 */
fun main() {
    val config = try {
        ConfigLoader.load()
    } catch (e: IllegalStateException) {
        System.err.println(e.message)
        kotlin.system.exitProcess(1)
    }
    logger.info("Starting Voice Bot application with config: $config")
    
    // initialize telegram client
    TelegramClient.init(config.telegramBotToken)
    
    // configure webhook handler
    WebhookHandler.configure(config.enableTestAcks)
    
    val startTime = System.currentTimeMillis()
    
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        
        install(ForwardedHeaders)
        install(XForwardedHeaders)
        
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                val clientIp = call.request.headers["X-Forwarded-For"] ?: call.request.local.remoteHost
                logger.error("Unhandled exception in request ${call.request.uri} from $clientIp", cause)
                // TODO: Later this will send DM to admin via Telegram
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("internal_error", "Internal server error occurred")
                )
            }
            
            status(HttpStatusCode.NotFound) { call, _ ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Endpoint not found"))
            }
        }
        
        routing {
            get("/health") {
                // Admin-only endpoint
                val adminToken = call.request.headers["X-Admin-Token"]
                if (adminToken != config.adminHttpToken) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid admin token"))
                    return@get
                }
                
                val uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000
                call.respond(HealthResponse("ok", uptimeSeconds))
            }
            
            get("/metrics") {
                // Admin-only endpoint
                val adminToken = call.request.headers["X-Admin-Token"]
                if (adminToken != config.adminHttpToken) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid admin token"))
                    return@get
                }
                
                val uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000
                // TODO: Replace with actual metrics once implemented
                call.respond(MetricsResponse(uptimeSeconds = uptimeSeconds))
            }
            
            // Webhook endpoint placeholder - will be implemented in later phases
            post("/webhook/{path}") {
                val webhookPath = call.parameters["path"]
                if (webhookPath != config.webhookPath) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                
                // Validate webhook secret
                val telegramSecret = call.request.headers["X-Telegram-Bot-Api-Secret-Token"]
                if (telegramSecret != config.webhookSecret) {
                    val clientIp = call.request.headers["X-Forwarded-For"] ?: call.request.local.remoteHost
                    logger.warn("Invalid webhook secret from $clientIp")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                WebhookHandler.handle(call)
            }
            
        }
    }.start(wait = true)
}