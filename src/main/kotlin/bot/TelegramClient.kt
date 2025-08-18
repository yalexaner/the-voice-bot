package bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.request.SendMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

object TelegramClient {
    private val logger = LoggerFactory.getLogger(TelegramClient::class.java)
    private lateinit var bot: TelegramBot

    fun init(botToken: String) {
        if (::bot.isInitialized) {
            logger.debug("telegram client already initialized, skipping")
            return
        }
        bot = TelegramBot(botToken)
        logger.info("telegram client initialized")
    }

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