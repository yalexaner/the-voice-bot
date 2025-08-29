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

/**
 * Extract the real client IP from X-Forwarded-For header or fall back to remote host.
 * X-Forwarded-For can contain multiple IPs: "client, proxy1, proxy2" - we want the first one.
 */
private fun ApplicationCall.getClientIp(): String {
    val forwardedFor = request.headers["X-Forwarded-For"]
    return if (forwardedFor != null) {
        // Take the first IP from the comma-separated list and trim whitespace
        forwardedFor.split(",").first().trim()
    } else {
        request.local.remoteHost
    }
}

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
                val clientIp = call.getClientIp()
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
            post("/webhook/${config.webhookPath}") {
                // Validate webhook secret
                val telegramSecret = call.request.headers["X-Telegram-Bot-Api-Secret-Token"]
                if (telegramSecret != config.webhookSecret) {
                    val clientIp = call.getClientIp()
                    logger.warn("Invalid webhook secret from $clientIp")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                WebhookHandler.handle(call)
            }
            
        }
    }.start(wait = true)
}