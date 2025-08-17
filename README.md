# Telegram Voice-to-Text Bot

A Telegram bot that transcribes voice messages to Russian text using ElevenLabs API. Built with Kotlin, Ktor, and Docker for production deployment with automatic HTTPS via Caddy.

## Features

- **Voice-to-Text**: Converts Telegram voice messages (OGG/Opus) to Russian text
- **Authorization System**: Whitelist-based user access with admin controls
- **Invite System**: Generate one-time invite tokens for new users
- **Admin Commands**: User management (`/allow`, `/deny`, `/list`, `/invite`)
- **Security**: Webhook validation, admin-only endpoints, non-root containers
- **Docker Deployment**: Complete Docker Compose setup with Caddy reverse proxy
- **Audio Processing**: FFmpeg integration for audio format conversion
- **Error Handling**: Graceful error handling with admin notifications

## Technology Stack

- **Language**: Kotlin (JDK 21)
- **Framework**: Ktor (webhook-based Telegram bot)
- **Database**: SQLite with Exposed ORM
- **Reverse Proxy**: Caddy (automatic HTTPS)
- **Deployment**: Docker Compose
- **Audio**: FFmpeg for OGG/Opus to WAV conversion
- **API**: ElevenLabs for voice transcription

## Quick Start

### Prerequisites

- Docker 27+ and Docker Compose v2
- Domain name with A record pointing to your server
- Telegram Bot Token (from @BotFather)
- Your Telegram User ID (send /start to @userinfobot)
- ElevenLabs API Key

### Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd TheVoiceBot
   ```

2. **Configure environment variables**
   ```bash
   cp .env.example .env
   # Edit .env with your actual values
   ```

3. **Update domain in Caddyfile**
   ```bash
   # Edit Caddyfile and replace "your-domain.com" with your actual domain
   ```

4. **Build and start services**
   ```bash
   # For staging
   docker compose -f docker-compose.staging.yml up -d --build
   
   # For production
   docker compose -f docker-compose.prod.yml up -d --build
   ```

5. **Set Telegram webhook**
   ```bash
   # Replace with your actual values
   curl -X POST "https://api.telegram.org/bot<BOT_TOKEN>/setWebhook" \
     -H "Content-Type: application/json" \
     -d '{
       "url": "https://your-domain.com/webhook/<WEBHOOK_PATH>",
       "secret_token": "<WEBHOOK_SECRET>"
     }'
   ```

## Environment Variables

Copy `.env.example` to `.env` and configure:

| Variable | Description | Required |
|----------|-------------|----------|
| `TELEGRAM_BOT_TOKEN` | Bot token from @BotFather | Yes |
| `TELEGRAM_ADMIN_ID` | Your Telegram user ID | Yes |
| `ELEVENLABS_API_KEY` | ElevenLabs API key | Yes |
| `WEBHOOK_PATH` | Random webhook path (32+ chars) | Yes |
| `WEBHOOK_SECRET` | Webhook validation secret (64+ chars) | Yes |
| `ADMIN_HTTP_TOKEN` | Token for /health and /metrics | Yes |
| `ENV` | Environment (staging/prod) | No (default: staging) |
| `PORT` | Server port | No (default: 8080) |

## Development

### Local Development

1. **Install dependencies**
   ```bash
   ./gradlew build
   ```

2. **Run locally**
   ```bash
   # Set environment variables
   export TELEGRAM_BOT_TOKEN="your_token"
   export TELEGRAM_ADMIN_ID="your_id"
   # ... other variables
   
   ./gradlew run
   ```

### Project Structure

```
src/main/kotlin/
├── Application.kt          # Main Ktor application
├── bot/                   # Telegram webhook handling
├── config/                # Configuration management
│   └── Config.kt         # Environment variable loading
├── db/                   # Database access (SQLite)
├── admin/                # Admin command handlers
├── elevenlabs/           # ElevenLabs API client
└── util/                 # Audio conversion, utilities
```

### Build Commands

```bash
# Build application
./gradlew build

# Create fat JAR
./gradlew jar

# Run tests (when implemented)
./gradlew test

# Clean build
./gradlew clean
```

## Deployment

### Staging Deployment

```bash
docker compose -f docker-compose.staging.yml up -d --build
```

### Production Deployment

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

### Health Checks

```bash
# Health endpoint (requires admin token)
curl -H "X-Admin-Token: your_admin_token" https://your-domain.com/health

# Metrics endpoint
curl -H "X-Admin-Token: your_admin_token" https://your-domain.com/metrics
```

## Security

- All secrets managed via environment variables
- Webhook path is randomized for security
- Webhook requests validated with secret token
- Admin endpoints protected with separate token
- Docker containers run as non-root user
- Caddy provides automatic HTTPS with proper security headers

## Backup and Maintenance

- SQLite database stored in Docker volume
- Nightly backups (to be implemented)
- 7-day backup retention
- Graceful shutdown support

## Bot Commands

### User Commands
- `/start` - Welcome message and authorization check
- `/help` - Usage instructions

### Admin Commands (Admin Only)
- `/allow <username>` - Authorize user
- `/deny <username>` - Revoke user access
- `/list` - Show all users and their status
- `/invite` - Generate one-time invite token

## Development Status

This project is currently in the initial setup phase. The following components are implemented:

✅ **Project Bootstrap** (Gradle, dependencies, structure)  
✅ **Configuration Management** (environment variables, validation)  
✅ **Docker Setup** (multi-stage build, Compose files)  
✅ **Ktor Skeleton** (/health, /metrics endpoints)  
✅ **Caddy Configuration** (reverse proxy, HTTPS)  

🚧 **Next Steps** (see docs/spec/todo.md for detailed checklist):
- Telegram webhook handling
- Database setup (SQLite, migrations)
- Admin commands implementation
- Voice message processing
- ElevenLabs API integration

## Contributing

1. Follow the development checklist in `docs/spec/todo.md`
2. Refer to the detailed specification in `docs/spec/spec.md`
3. Use the established project structure and coding conventions
4. Test all changes thoroughly before deployment

## License

[Add your license here]