package telegram

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.pengrad.telegrambot.model.Update
import org.slf4j.LoggerFactory

object UpdateParser {
    private val gson = Gson()
    private val logger = LoggerFactory.getLogger(UpdateParser::class.java)
    
    fun parse(json: String): Update? {
        return try {
            gson.fromJson(json, Update::class.java)
        } catch (e: JsonSyntaxException) {
            // log truncated payload to avoid PII leakage
            val snippet = json.take(512)
            logger.debug("failed to parse update: ${e.message}. payload: $snippet", e)
            null
        } catch (e: Exception) {
            logger.debug("unexpected error parsing update: ${e.message}", e)
            null
        }
    }
}