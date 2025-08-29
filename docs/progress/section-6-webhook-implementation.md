# Section 6: Telegram Webhook Implementation

**Date**: 2025-08-18  
**Status**: ✅ Completed  
**Files**: `bot/TelegramClient.kt`, `bot/WebhookHandler.kt`, `Application.kt`, `config/Config.kt`

## Overview

Implemented complete Telegram webhook receiver with Update parsing, message type classification, and test acknowledgment system. This foundation enables the bot to receive and process all types of Telegram updates.

## Implementation Approach

### Architecture Decision: Modular Design
- **TelegramClient**: Isolated Telegram API operations (sendMessage helper)
- **WebhookHandler**: Request processing, parsing, and routing logic
- **UpdateParser → BotUtils**: JSON parsing (evolved during implementation)

### Key Components

#### 1. TelegramClient.kt
```kotlin
object TelegramClient {
    private lateinit var bot: TelegramBot
    
    fun init(botToken: String)              // Idempotent initialization
    suspend fun sendMessage(...)            // IO-dispatched with guards
}
```

#### 2. WebhookHandler.kt  
```kotlin
object WebhookHandler {
    suspend fun handle(call: ApplicationCall)     // Main webhook processor
    private fun determineUpdateType(update)       // Message classification
    private fun chatIdOf(update)                  // Universal chat extraction
}
```

#### 3. Configuration Integration
```kotlin
data class Config(
    val enableTestAcks: Boolean = false    // Production safety flag
)
```

## Critical Issues Encountered & Solutions

### 🚨 **Issue 1: Production Safety - Unconditional Acknowledgments**
**Problem**: Every webhook triggered ack messages → user spam  
**Solution**: Configuration flag `ENABLE_TEST_ACKS` (default: false)
```kotlin
if (ackEnabled && !isBot && chatId != null && messageId != null) {
    // Send ack only when explicitly enabled
}
```

### 🚨 **Issue 2: Bot Message Echo Loops**  
**Problem**: Bot responding to its own messages → infinite loops  
**Solution**: Bot origin detection before acknowledgments
```kotlin
val from = update.message()?.from()
val isBot = from?.isBot == true
if (ackEnabled && !isBot && ...) { /* safe to ack */ }
```

### 🚨 **Issue 3: Initialization Race Conditions**
**Problem**: `sendMessage` called before `TelegramClient.init()` → crashes  
**Solution**: Initialization guards in both methods
```kotlin
fun init(botToken: String) {
    if (::bot.isInitialized) return  // Idempotent
}

suspend fun sendMessage(...) {
    if (!::bot.isInitialized) {
        logger.warn("dropping message, not initialized")
        return
    }
}
```

### 🔧 **Issue 4: JSON Response Inconsistency**
**Problem**: Mix of `mapOf()` and plain responses → serialization issues  
**Solution**: Consistent DTO usage
```kotlin
@Serializable
data class WebhookAck(val status: String)
// All responses use: WebhookAck("ok")
```

### 🔧 **Issue 5: Limited Update Type Coverage**
**Problem**: Only `message()` chatId extraction → null for other updates  
**Solution**: Universal chatId extraction
```kotlin
private fun chatIdOf(update: Update): Long? = when {
    update.message() != null -> update.message().chat()?.id()
    update.editedMessage() != null -> update.editedMessage().chat()?.id()
    update.channelPost() != null -> update.channelPost().chat()?.id()
    update.callbackQuery() != null -> update.callbackQuery().message()?.chat()?.id()
    else -> null
}
```

### 🔧 **Issue 6: Webhook Response Timing**
**Problem**: Synchronous Telegram API calls → webhook timeouts  
**Solution**: Async message sending
```kotlin
call.application.launch {
    TelegramClient.sendMessage(...)  // Non-blocking
}
call.respond(HttpStatusCode.OK)      // Immediate response
```

## Technical Improvements Applied

### 1. **Robust JSON Parsing**
- **Initial**: Custom Gson wrapper (`UpdateParser.kt`)
- **Final**: Pengrad's `BotUtils.parseUpdate()` with `runCatching`
- **Benefit**: Better error handling, fewer dependencies

### 2. **Enhanced Command Detection**
- **Basic**: `text?.startsWith("/")`
- **Improved**: MessageEntity inspection + regex fallback
- **Pattern**: `^/[A-Za-z_]+(?:@\w+)?(?: .*)?$`
- **Benefit**: Prevents false positives (file paths, etc.)

### 3. **Content-Type Validation**
```kotlin
val contentType = call.request.headers[HttpHeaders.ContentType]
if (contentType?.startsWith(ContentType.Application.Json.toString()) != true) {
    logger.warn("Unexpected Content-Type: {}", contentType)
}
```

### 4. **Extended Update Classification**
```kotlin
private fun determineUpdateType(update: Update): String {
    if (update.editedMessage() != null) return "edited_message"
    if (update.channelPost() != null) return "channel_post" 
    if (update.callbackQuery() != null) return "callback_query"
    // ... message types
}
```

## Key Lessons Learned

### 🎯 **Production Readiness Considerations**
1. **Always gate test/debug features** behind configuration flags
2. **Prevent infinite loops** by checking message origins
3. **Validate webhook timing** - Telegram has strict timeout requirements
4. **Use consistent response formats** to avoid serialization issues

### 🎯 **Error Handling Patterns**
1. **Idempotent initialization** prevents duplicate setup issues
2. **Graceful degradation** - log errors but always return 200 OK
3. **Guard clauses** prevent crashes from uninitialized state
4. **Async operations** for non-critical webhook responses

### 🎯 **Code Organization**
1. **Separate concerns**: Client operations vs request handling
2. **Universal helpers**: `chatIdOf()` works across all update types
3. **Configuration-driven behavior**: Runtime toggles for different environments
4. **Library integration**: Prefer official utilities over custom parsing

## Future Considerations

### When Implementing Similar Features
- Start with production safety flags for any user-facing behavior
- Consider all Telegram update types, not just messages
- Always implement async patterns for external API calls
- Add comprehensive logging with update IDs for debugging

### When Debugging Similar Issues
- Check initialization order if getting `lateinit` crashes
- Look for infinite loops in message processing
- Verify webhook response timing if getting delivery issues
- Validate JSON serialization consistency across all endpoints

### 🔐 Security: Webhook Secret Token Validation
To prevent spoofing, Telegram webhooks can be secured with a secret token. When you register your webhook, you can provide a secret token. This token will be sent with every webhook request in the `X-Telegram-Bot-Api-Secret-Token` header.

It is critical to validate this token on the server-side to ensure that the request is from Telegram. The application does this automatically by comparing the header with the `WEBHOOK_SECRET` environment variable. If they do not match, the request is rejected with a `401 Unauthorized` status.

Here is the implementation from `Application.kt`:
```kotlin
val telegramSecret = call.request.headers["X-Telegram-Bot-Api-Secret-Token"]
if (telegramSecret != config.webhookSecret) {
    val clientIp = call.request.headers["X-Forwarded-For"] ?: call.request.local.remoteHost
    logger.warn("Invalid webhook secret from $clientIp")
    call.respond(HttpStatusCode.Unauthorized)
    return
}
```

**Important Security Note:** Invalid webhook tokens return `401 Unauthorized` rather than `200 OK`. This is crucial because:
- It allows proper monitoring and alerting of potential security breaches
- Telegram will retry non-2xx responses, helping detect configuration issues
- A `200 OK` response would silently ignore attacks, making them undetectable

## Dependencies Added
```kotlin
implementation("com.google.code.gson:gson:2.11.0")  // Required by Pengrad BotUtils for JSON parsing
```

## Environment Variables
```bash
ENABLE_TEST_ACKS=false  # Production safety flag
```

### 🛡️ Production Safety: ENABLE_TEST_ACKS Flag

The `ENABLE_TEST_ACKS` environment variable controls whether the bot sends acknowledgment messages for testing purposes. 

**Purpose:**
- When enabled (`true`), the bot responds to updates with "✅ {updateType} received" messages
- Used for development and testing to verify webhook delivery and parsing
- Should **always** be `false` in production environments

**Security Enforcement:**
The application includes startup validation that **prevents** launching in production with test acks enabled:

```kotlin
// Production environment security checks
if (env == "prod") {
    if (enableTestAcks) {
        errors.add("ENABLE_TEST_ACKS must be disabled (false) in production environment for security")
    }
}
```

**Why This Matters:**
- Test acknowledgments can spam user chats with system messages
- May reveal internal bot logic and update processing to users
- Could interfere with real bot functionality in production
- Violates the principle of clean production deployments