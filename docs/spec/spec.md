# Telegram Voice-to-Text Bot — Developer Specification

## Overview

A Telegram bot that receives **voice messages (OGG/Opus)** from authorized users, transcribes them to text using **ElevenLabs API**, and replies back with the transcript in **Russian**. MVP designed for low-volume hobby use (\~200 messages/day).

---

## Core Features

1. **Voice-to-Text Pipeline**

   * Accept only Telegram **voice messages**.
   * Download OGG/Opus file via Telegram API.
   * Convert audio → **WAV PCM 16 kHz mono** using `ffmpeg`.
   * Send WAV to **ElevenLabs transcription API** (fixed to Russian).
   * Reply with plain text as a **reply to original voice message**.
   * Reject voice messages **>90s** with a friendly error.

2. **Authorization**

   * Only whitelisted users can use the bot.
   * Single **admin user** (owner).
   * Admin commands (hidden from normal users):

     * `/allow <username>` — authorize user.
     * `/deny <username>` — revoke access.
     * `/list` — show authorized users.
     * `/invite` — generate one-time invite token.
   * Normal users see: *«Эта команда не поддерживается»* for all admin commands.
   * Invites: `/start <token>` flow to self-register.

3. **User Commands**

   * `/start` — welcome + authorization check.
   * `/help` — instructions: send a voice message to get text back.

4. **Error Handling**

   * If ElevenLabs API fails → **retry once**, then reply with error message.
   * If user not authorized → reply with error.
   * Errors send **Telegram DM to admin** with **full stack trace**.

---

## Architecture

* **Language**: Kotlin (JDK 21)
* **Framework**: Ktor (single service)
* **Bot interaction**: Telegram webhook
* **Reverse proxy & TLS**: Caddy (Docker)
* **Database**: SQLite (via Docker volume)
* **Deployment**: Docker Compose (bot + Caddy + DB volume)
* **Logging**: simple console logs

### Project Structure

```
src/main/kotlin/
  bot/        → Telegram webhook handling
  elevenlabs/ → ElevenLabs API client
  db/         → SQLite access (users, invites)
  admin/      → Admin command handlers
  config/     → Env vars, settings
  util/       → Audio conversion, error handling
```

---

## Data Handling

### Database (SQLite)

* `users`

  * `telegram_user_id` (PK)
  * `username`
  * `status` (active/blocked)
  * `added_by`
  * `added_at`
* `invites`

  * `token` (PK)
  * `created_by`
  * `created_at`
  * `expires_at` (nullable)
  * `used_by` (nullable)
  * `used_at` (nullable)

### Retention

* **No transcripts stored** in DB.
* Backups only for auth DB.

### Backups

* Nightly `sqlite3 dump` → timestamped `.sql` file.
* Retain **last 7 days** → prune older.
* Restore procedure: stop bot → restore dump → restart.

---

## Error Handling & Resilience

* **Graceful shutdown**: finish ongoing transcriptions before exit.
* **ElevenLabs failures**: retry once, then notify user.
* **Unexpected errors**: log + notify admin via Telegram.
* **Webhook security**: random path + `X-Telegram-Bot-Api-Secret-Token` validation.

---

## Admin Endpoints

* `/health` — returns `{ "status": "ok", "uptime": "123s" }`, admin-only.
* `/metrics` — total requests, avg transcription time, errors, uptime. Admin-only.

---

## Environment Variables

* `TELEGRAM_BOT_TOKEN` — bot token.
* `TELEGRAM_ADMIN_ID` — admin user ID.
* `ELEVENLABS_API_KEY` — ElevenLabs key.
* `WEBHOOK_SECRET` — secret for webhook validation.
* `WEBHOOK_PATH` — random webhook URL path.
* `ENV` — `staging` or `prod`.

---

## Deployment

### Tools & Versions

* Docker 27+
* Docker Compose v2
* JDK 21
* Kotlin 2.x
* Gradle 8+
* ffmpeg 6+

### Deployment Checklist

1. Install Docker & Compose.
2. Install `ffmpeg` on host.
3. Clone repo.
4. Create `.env` with required variables.
5. Run `docker compose -f docker-compose.prod.yml up -d`.
6. Set Telegram webhook → `https://domain/<WEBHOOK_PATH>` with secret.

### Backups & Maintenance

* Backups run nightly (cron).
* Restore: `sqlite3 bot.db < backup.sql`.
* Maintenance tasks:

  * `docker system prune` for old images.
  * Check disk usage.
  * Rotate logs.

---

## CI/CD

* GitHub Actions:

  * On push to `main`: build + push Docker image (tagged `latest` + version).
  * On release tag (`vX.Y.Z`): build + deploy to production.
* Deployment script: `make deploy-prod`, `make deploy-staging`.
* Staging auto-updates from main; production updates only on tagged releases.

---

## Security Checklist

* Secrets via env vars only.
* Webhook secret validation.
* Firewall: allow only 22 (SSH), 443 (HTTPS).
* SSH: disable root login, key-based auth.
* Docker: non-root containers, read-only FS where possible.
* Keep dependencies updated.
* Avoid storing sensitive data in DB.

---

## Sample Bot Messages (Russian)

* `/start` (authorized): «Добро пожаловать! Отправь голосовое сообщение, и я пришлю текст.»
* `/start` (unauthorized): «У вас нет доступа. Обратитесь к администратору.»
* `/help`: «Отправь голосовое сообщение, и бот вернёт текстовую расшифровку.»
* Voice >90s: «Сообщение слишком длинное (более 90 секунд).»
* Not authorized: «У вас нет доступа.»
* Unknown command (non-admin): «Эта команда не поддерживается.»
* ElevenLabs error: «Не удалось расшифровать сообщение. Попробуйте позже.»

---

## Testing Plan (Minimal)

* Send voice message <90s (authorized) → get transcript.
* Send voice message >90s → error.
* Unauthorized user `/start` → denied.
* Admin `/allow` + invite flow.
* ElevenLabs failure simulation → retry + error.
* Graceful shutdown test with ongoing request.
* `/health` + `/metrics` check (admin only).

---

## Future Improvements

* Multi-language transcription.
* Support for audio files (MP3, WAV, M4A).
* Group chat support.
* Transcript history storage/search.
* Rate limiting & quotas.
* Noise reduction before transcription.
* Web dashboard for admin control.
