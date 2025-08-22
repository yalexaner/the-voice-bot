package telegram

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.pengrad.telegrambot.model.Update
import org.slf4j.LoggerFactory

object UpdateParser {
    private val gson = Gson()
    private val logger = LoggerFactory.getLogger(UpdateParser::class.java)
    
    /**
     * Parse a JSON string into a Telegram [Update] object.
     *
     * Attempts to deserialize the provided JSON into an [Update]. On success returns the parsed
     * Update; on failure returns null. If the JSON is syntactically invalid a truncated (up to
     * 512-char) payload snippet is logged at debug level to avoid leaking PII; other errors are
     * also logged at debug level.
     *
     * @param json The JSON payload representing a Telegram update.
     * @return The deserialized [Update], or `null` if parsing failed.
     */
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