package bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.request.SendMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

object TelegramClient {
    private val logger = LoggerFactory.getLogger(TelegramClient::class.java)
    private lateinit var bot: TelegramBot

    /**
     * Initializes the singleton Telegram bot client with the provided bot token.
     *
     * This function is idempotent — if the client is already initialized, the call is ignored.
     *
     * @param botToken The Telegram bot token (from BotFather). Keep this token secret.
     */
    fun init(botToken: String) {
        if (::bot.isInitialized) {
            logger.debug("telegram client already initialized, skipping")
            return
        }
        bot = TelegramBot(botToken)
        logger.info("telegram client initialized")
    }

    /**
     * Sends a text message to a Telegram chat via the initialized bot.
     *
     * If the bot has not been initialized, this function logs a warning and returns without sending.
     * The send operation is performed on Dispatchers.IO; any exceptions raised while sending are caught
     * and logged (they do not propagate).
     *
     * @param chatId Target chat identifier.
     * @param text Message text to send.
     * @param replyToMessageId Optional message id to reply to; when non-null the message will be sent as a reply.
     */
    suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Int? = null) {
        if (!::bot.isInitialized) {
            logger.warn("telegram client not initialized; dropping message to chat {}", chatId)
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val request = SendMessage(chatId, text).apply {
                    replyToMessageId?.let { replyToMessageId(it) }
                }
                val response = bot.execute(request)
                if (!response.isOk) {
                    logger.error("failed to send message to chat {}: {}", chatId, response.description())
                }
            } catch (e: Exception) {
                logger.error("error sending message to chat {}: {}", chatId, e.message, e)
            }
        }
    }
}