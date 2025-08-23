package bot

import com.pengrad.telegrambot.utility.BotUtils
import com.pengrad.telegrambot.model.MessageEntity
import com.pengrad.telegrambot.model.Update
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

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
            val ct = call.request.contentType()
            if (ct.withoutParameters() != ContentType.Application.Json) {
                logger.warn("unexpected content-type: {}", ct)
            }
            
            // parse JSON before responding
            val rawJson = call.receiveText()
            val update = try {
                BotUtils.parseUpdate(rawJson)
            } catch (e: Exception) {
                logger.debug("failed to parse telegram update", e)
                call.respond(HttpStatusCode.BadRequest, "invalid json")
                return
            }
            
            // respond OK immediately after successful parse
            call.respond(HttpStatusCode.OK, WebhookAck("ok"))
            
            // process update asynchronously
            call.application.launch {
                processUpdate(update)
            }
        } catch (e: Exception) {
            logger.error("error processing webhook", e)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
    
    private suspend fun processUpdate(update: Update) {
        try {
            val updateType = determineUpdateType(update)
            val chatId = chatIdOf(update)
            val messageId = update.message()?.messageId()
            
            logger.info("processing update {} of type '{}' from chat {}", update.updateId(), updateType, chatId)
            
            // send acknowledgment message for testing (async)
            val from = update.message()?.from()
            val isBot = from?.isBot == true
            if (ackEnabled && !isBot && chatId != null && messageId != null) {
                TelegramClient.sendMessage(chatId, "✅ $updateType received", messageId)
            }
        } catch (e: Exception) {
            logger.error("error processing update {}", update.updateId(), e)
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