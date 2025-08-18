package bot

import com.pengrad.telegrambot.model.MessageEntity
import com.pengrad.telegrambot.model.Update
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import telegram.UpdateParser

@Serializable
data class WebhookAck(val status: String)

object WebhookHandler {
    private val logger = LoggerFactory.getLogger(WebhookHandler::class.java)
    private var ackEnabled: Boolean = false
    
    fun configure(ack: Boolean) {
        ackEnabled = ack
    }

    suspend fun handle(call: ApplicationCall) {
        try {
            // validate content type
            val contentType = call.request.headers[HttpHeaders.ContentType]
            if (contentType?.startsWith(ContentType.Application.Json.toString()) != true) {
                logger.warn("Unexpected Content-Type: {}", contentType)
            }
            
            val rawJson = call.receiveText()
            val update = UpdateParser.parse(rawJson)
            
            if (update == null) {
                logger.warn("received null or invalid update from parsing")
                call.respond(HttpStatusCode.OK, WebhookAck("ok"))
                return
            }
            
            val updateType = determineUpdateType(update)
            val chatId = chatIdOf(update)
            val messageId = update.message()?.messageId()
            
            logger.info("received update {} of type '{}' from chat {}", update.updateId(), updateType, chatId)
            
            // send acknowledgment message for testing (async to avoid blocking webhook response)
            val from = update.message()?.from()
            val isBot = from?.isBot == true
            if (ackEnabled && !isBot && chatId != null && messageId != null) {
                call.application.launch {
                    TelegramClient.sendMessage(chatId, "✅ $updateType received", messageId)
                }
            }
            
            call.respond(HttpStatusCode.OK, WebhookAck("ok"))
        } catch (e: Exception) {
            logger.error("error processing webhook: {}", e.message, e)
            call.respond(HttpStatusCode.OK, WebhookAck("ok"))
        }
    }
    
    private fun determineUpdateType(update: Update): String {
        // check for non-message update types first
        if (update.editedMessage() != null) return "edited_message"
        if (update.channelPost() != null) return "channel_post"
        if (update.callbackQuery() != null) return "callback_query"
        
        // check for message types
        val message = update.message() ?: return "unknown"
        
        return when {
            message.voice() != null -> "voice message"
            hasCommandEntity(message) -> "command"
            message.text()?.matches(Regex("^/[A-Za-z_]+(?:@\\w+)?(?: .*)?$")) == true -> "command"
            message.text() != null -> "text message"
            else -> "other"
        }
    }
    
    private fun chatIdOf(update: Update): Long? = when {
        update.message() != null -> update.message().chat()?.id()
        update.editedMessage() != null -> update.editedMessage().chat()?.id()
        update.channelPost() != null -> update.channelPost().chat()?.id()
        update.callbackQuery() != null -> update.callbackQuery().message()?.chat()?.id()
        else -> null
    }
    
    private fun hasCommandEntity(message: com.pengrad.telegrambot.model.Message): Boolean {
        val entities = message.entities() ?: return false
        return entities.any { it.type() == MessageEntity.Type.bot_command }
    }
}