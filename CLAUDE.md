# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Telegram voice-to-text bot that transcribes voice messages to Russian text using ElevenLabs API. The project is currently in planning/specification phase with detailed documentation but no implementation yet.

## Architecture & Technology Stack

**Language & Framework:**
- Kotlin (JDK 21)
- Ktor framework (webhook-based Telegram bot)
- SQLite database (user authorization and invite tokens)
- Docker Compose deployment (bot + Caddy reverse proxy)

**Key Components:**
- `bot/` - Telegram webhook handling and message processing
- `elevenlabs/` - ElevenLabs API client for voice transcription
- `db/` - SQLite database access (users and invites tables)
- `admin/` - Admin command handlers (/allow, /deny, /list, /invite)
- `config/` - Environment variable configuration
- `util/` - Audio conversion (ffmpeg), error handling utilities

## Development Workflow

Since the project is not yet implemented, development should follow the detailed build plan in `docs/spec/prompt_plan.md` which breaks down implementation into phases:

1. **Phase 1** - Bootstrap & Infrastructure (Gradle, Kotlin, Ktor, Docker)
2. **Phase 2** - Telegram Webhook Skeleton (webhook validation, basic commands)
3. **Phase 3** - Auth & Admin (SQLite, user management, invite system)
4. **Phase 4** - Voice Pipeline (download, convert with ffmpeg, transcribe with ElevenLabs)
5. **Phase 5** - Production (metrics, graceful shutdown, backups, CI/CD)

## Key Requirements

**Audio Processing:**
- Accept only Telegram voice messages (OGG/Opus format)
- Convert to WAV PCM 16 kHz mono using ffmpeg
- Reject voice messages longer than 90 seconds
- ElevenLabs API integration with Russian language setting

**Authorization System:**
- Whitelist-based user access (stored in SQLite)
- Single admin user (from TELEGRAM_ADMIN_ID env var)
- Invite token system for user registration
- Admin commands hidden from regular users

**Error Handling:**
- Retry ElevenLabs API calls once on failure
- Send stack traces to admin via Telegram DM
- User-friendly Russian error messages
- Graceful shutdown with in-flight request completion

## Environment Variables

Required configuration (see `.env.example` when created):
- `TELEGRAM_BOT_TOKEN` - Bot token from @BotFather
- `TELEGRAM_ADMIN_ID` - Admin user's Telegram ID
- `ELEVENLABS_API_KEY` - ElevenLabs transcription API key
- `WEBHOOK_SECRET` - Secret for webhook validation
- `WEBHOOK_PATH` - Random webhook URL path for security
- `ADMIN_HTTP_TOKEN` - For /health and /metrics endpoints

## Database Schema

**users table:**
- `telegram_user_id` (PK), `username`, `status` (active/blocked), `added_by`, `added_at`

**invites table:**
- `token` (PK), `created_by`, `created_at`, `expires_at`, `used_by`, `used_at`

## Deployment

- Docker Compose with bot service and Caddy reverse proxy
- Caddy handles automatic HTTPS/TLS certificates
- SQLite database persisted via Docker volume
- Nightly backups with 7-day retention
- Admin-only endpoints: `/health` and `/metrics`

## Implementation Notes

When implementing:
1. Follow the micro-step breakdown in `docs/spec/prompt_plan.md` for incremental development
2. Use the checklist in `docs/spec/todo.md` to track progress
3. All user-facing messages should be in Russian
4. Implement proper webhook security (secret token validation)
5. Ensure no transcripts are stored in the database (privacy requirement)
6. Set up error notifications to admin via Telegram DM

## Testing Strategy

Manual testing workflow:
- Voice message transcription (authorized users)
- Duration limits (>90s rejection)
- Authorization flow (invite tokens)
- Admin commands (/allow, /deny, /list, /invite)
- Error handling (ElevenLabs failures)
- Graceful shutdown during active requests