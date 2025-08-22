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
    
    /**
     * Enables or disables sending asynchronous acknowledgement messages for processed webhooks.
     *
     * @param ack If true, the handler will send an asynchronous "received" acknowledgement back to the chat when possible; if false, acknowledgements are disabled.
     */
    fun configure(ack: Boolean) {
        ackEnabled = ack
    }

    /**
     * Process an incoming Ktor ApplicationCall carrying a Telegram webhook update.
     *
     * Reads the request body as JSON, parses it into a Telegram Update, classifies the update
     * type and extracts chat/message identifiers. Always responds HTTP 200 with a
     * WebhookAck("ok"). If parsing fails, logs a warning and still responds 200 OK.
     *
     * Side effects:
     * - Logs update reception and any errors.
     * - May asynchronously send an acknowledgment message via TelegramClient when
     *   acknowledgements are enabled, the sender is not a bot, and both chatId and
     *   messageId are present.
     *
     * Errors thrown while processing are caught, logged, and result in a 200 OK response.
     */
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
    
    /**
     * Classifies a Telegram Update into a short, human-readable type string.
     *
     * Determines the most specific update type available: checks non-message update kinds
     * (edited message, channel post, callback query) first, then inspects the contained
     * message for voice, bot command, plain text, or other message forms.
     *
     * @param update The parsed Telegram Update to classify.
     * @return A short string describing the update type (e.g. "edited_message", "channel_post",
     * "callback_query", "voice message", "command", "text message", "other", or "unknown").
     */
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
    
    /**
     * Extracts the Telegram chat ID from an Update, if present.
     *
     * Checks `message`, `editedMessage`, `channelPost`, and `callbackQuery.message` (in that order)
     * and returns the first non-null chat id.
     *
     * @param update The Telegram Update to inspect.
     * @return The chat id if found, otherwise `null`.
     */
    private fun chatIdOf(update: Update): Long? = when {
        update.message() != null -> update.message().chat()?.id()
        update.editedMessage() != null -> update.editedMessage().chat()?.id()
        update.channelPost() != null -> update.channelPost().chat()?.id()
        update.callbackQuery() != null -> update.callbackQuery().message()?.chat()?.id()
        else -> null
    }
    
    /**
     * Checks whether the given Telegram message contains a `bot_command` entity.
     *
     * Returns true if the message has at least one entity of type `bot_command`. If the message has no entities (null),
     * the function returns false.
     *
     * @return true when a `bot_command` entity is present; false otherwise.
     */
    private fun hasCommandEntity(message: com.pengrad.telegrambot.model.Message): Boolean {
        val entities = message.entities() ?: return false
        return entities.any { it.type() == MessageEntity.Type.bot_command }
    }
}