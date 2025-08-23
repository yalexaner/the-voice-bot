package bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.request.SendMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

object TelegramClient {
    private val logger = LoggerFactory.getLogger(TelegramClient::class.java)
    
    private var botToken: String? = null
    private val bot: TelegramBot by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val token = botToken ?: error("TelegramBot not initialized - call init() first")
        logger.info("initializing telegram bot")
        TelegramBot(token)
    }

    fun init(botToken: String) {
        this.botToken = botToken
        logger.debug("telegram client configured")
    }

    suspend fun sendMessage(chatId: Long, text: String, replyToId: Int? = null) {
        withContext(Dispatchers.IO) {
            try {
                val request = SendMessage(chatId, text).apply {
                    replyToId?.let { this.replyToMessageId(it) }
                }
                val response = bot.execute(request)
                if (!response.isOk) {
                    logger.error("failed to send message to chat {}: {}", chatId, response.description())
                }
            } catch (e: Exception) {
                logger.error("error sending message to chat {}", chatId, e)
            }
        }
    }
}