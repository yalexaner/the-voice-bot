# Telegram Voice Bot — Build Blueprint & Iterative Plan

This blueprint turns the spec into an actionable, iterative plan with multiple levels of breakdown. It’s designed so a developer can pick up the first task and ship value in hours, while keeping the system safe and verifiable at each step.

---

## 0) Context Snapshot (from the Spec)

* **Goal**: Telegram bot (private chats only) that transcribes Telegram **voice** messages (OGG/Opus) to **Russian text** using **ElevenLabs**, and replies as a thread to the original message.
* **Scale**: Hobby (\~≤200 msgs/day).
* **Stack**: Kotlin + **Ktor** (webhook), **Caddy** (TLS), **SQLite** (users & invites only), **Docker Compose** (bot + caddy + volumes), **ffmpeg** for audio conversion.
* **Security**: Random webhook path + Telegram secret header check. Admin-only endpoints.
* **Ops**: Simple logs, Telegram DM error notifications (with stack traces), nightly DB backups with 7-day retention, health/metrics endpoints (admin-only), graceful shutdown.
* **Scope**: MVP only; transcripts **not** stored.

---

# Part A — Step-by-Step Build Plan (High-Level Phases)

### Phase 1 — Bootstrap & Infrastructure (Local, then VPS)

1. Initialize repository, Gradle, Kotlin/JDK21, Ktor skeleton.
2. Add Dockerfile, Docker Compose (bot + caddy + volumes). Local dev via Compose.
3. Wire env config, logging, minimal `/health` (admin-protected), smoke test.

### Phase 2 — Telegram Webhook Skeleton

4. Add Telegram API client (pengrad/java-telegram-bot-api) & inbound webhook route in Ktor.
5. Implement webhook verification: random path + secret header check.
6. Implement a basic `/start` and `/help` command handling (static responses).

### Phase 3 — Auth & Admin

7. Add SQLite + migration (users, invites). Use Exposed or JDBC with a light DAO.
8. Implement admin-only commands: `/list`, `/allow`, `/deny`, `/invite`. Hide from non-admins.
9. Implement invite flow: `/start <token>` self-register.

### Phase 4 — Voice Pipeline

10. Accept and validate voice-only messages (reject others). Enforce 90s max duration.
11. Download OGG/Opus from Telegram File API.
12. Convert to WAV PCM 16 kHz mono via ffmpeg.
13. ElevenLabs transcription client (real API), fixed `ru`.
14. Reply with transcript as **reply to the voice**.
15. Error handling: single retry on ElevenLabs failure; user-facing error if still failing; DM admin with stack trace.

### Phase 5 — Ops & Production

16. Metrics (in-memory): totals, avg transcribe time, errors; admin-only endpoint.
17. Graceful shutdown; drain in-flight jobs.
18. Backups: nightly SQLite dump + 7-day retention (sidecar or host cron).
19. CI/CD: GitHub Actions (build & tag image; optional deploy). Makefile for local deploy.
20. VPS deploy: Caddy HTTPS, set Telegram webhook with secret.

---

# Part B — Iterative Chunks (First Pass)

Each chunk is independently verifiable; ship after each.

**Chunk 0: Repo & Local Skeleton**
Deliverables: Gradle project, Ktor hello-world, Dockerfile, docker-compose.staging.yml, `.env.example`.

**Chunk 1: Admin-Protected Health**
Deliverables: `/health` endpoint requiring `X-Admin-Token` header; returns `{"status":"ok"}`.

**Chunk 2: Telegram Webhook Receiver**
Deliverables: Ktor route at `/webhook/<random>` with secret header check; logs inbound updates; 200 OK.

**Chunk 3: Basic Commands**
Deliverables: `/start` & `/help` support via webhook update parsing; non-admin unknown commands → «Эта команда не поддерживается».

**Chunk 4: SQLite & Auth**
Deliverables: DB migrations; `users` & `invites`; admin-only `/list`, `/allow`, `/deny`; unauthorized users blocked.

**Chunk 5: Invite Flow**
Deliverables: `/invite` generates one-time token; `/start <token>` registers user and consumes token.

**Chunk 6: Voice Intake**
Deliverables: Accept Telegram voice messages; enforce 90s limit; reply error if exceeded.

**Chunk 7: Audio Download & Convert**
Deliverables: Download OGG/Opus; convert via ffmpeg to WAV PCM 16k mono.

**Chunk 8: ElevenLabs Transcribe**
Deliverables: Transcribe WAV to Russian text; retry once on failure; return transcript.

**Chunk 9: Error Notifications**
Deliverables: DM admin on unhandled exceptions and on ElevenLabs permanent failures with stack trace.

**Chunk 10: Metrics & Graceful Shutdown**
Deliverables: metrics endpoint; average latency & totals; graceful shutdown with draining.

**Chunk 11: Backups & Retention**
Deliverables: nightly dump of SQLite; keep last 7 days.

**Chunk 12: CI/CD & Prod Deploy**
Deliverables: GitHub Actions (build/tag/push; optional deploy); Caddy TLS; set Telegram webhook; run in prod Compose.

---

# Part C — Second Pass: Break Each Chunk into Small Steps (15–60 min each)

## Chunk 0 — Repo & Local Skeleton

0.1 Create repo, Gradle Kotlin JVM project (JDK 21), add Ktor deps (server-netty, serialization, content negotiation).
**AC**: `./gradlew run` prints hello.

0.2 Add Dockerfile (JDK 21 base) to run app.
**AC**: `docker build` and `docker run` prints hello.

0.3 Add docker-compose.staging.yml (services: bot, caddy; volumes: db\_data, caddy\_data).
**AC**: `docker compose -f docker-compose.staging.yml up` runs both; caddy serves placeholder.

0.4 Add `.env.example` with required env var names; wire simple config reader.
**AC**: app logs all required vars (masked) at startup.

## Chunk 1 — Admin-Protected Health

1.1 Add `/health` route; check `X-Admin-Token`==`ADMIN_HTTP_TOKEN`.
**AC**: `curl -H 'X-Admin-Token: ...' /health` → `{status:"ok"}`; wrong token → 401.

1.2 Add `uptimeSeconds` and include in JSON.
**AC**: value increases over time.

## Chunk 2 — Telegram Webhook Receiver

2.1 Create random `WEBHOOK_PATH` config; mount route `/webhook/<PATH>`.
**AC**: 404 for other paths.

2.2 Validate Telegram header `X-Telegram-Bot-Api-Secret-Token` equals `WEBHOOK_SECRET`; else 401.
**AC**: curl with header accepted; without rejected.

2.3 Parse `Update` JSON into model (use pengrad models or DTOs).
**AC**: Logs message type (text/voice/command).

## Chunk 3 — Basic Commands

3.1 Implement `/start` (authorized vs unauthorized message).
**AC**: Non-listed users get «У вас нет доступа…»

3.2 Implement `/help` static message (RU).
**AC**: Bot sends help text.

3.3 Default for unknown commands for non-admins: «Эта команда не поддерживается».
**AC**: Works.

## Chunk 4 — SQLite & Auth

4.1 Add SQLite driver & Exposed (or JDBC + DSL); migration scripts to create `users`, `invites`.
**AC**: Tables created on first run.

4.2 Seed admin user by `TELEGRAM_ADMIN_ID` env.
**AC**: `/list` shows admin as active.

4.3 Implement `/list` (admin only).
**AC**: Shows users with status.

4.4 Implement `/allow <username>` & `/deny <username>` (admin only).
**AC**: Updates `users.status`.

4.5 Enforce auth on message handling: block non-active users.
**AC**: Non-active user’s voice yields «У вас нет доступа».

## Chunk 5 — Invite Flow

5.1 `/invite` generates a random token, stores in DB, returns token in admin chat.
**AC**: Token visible to admin.

5.2 `/start <token>`: validate token not used/expired; create user active; consume token.
**AC**: New user can now use bot.

## Chunk 6 — Voice Intake

6.1 Detect voice messages; ignore other types.
**AC**: Non-voice triggers «Отправьте голосовое сообщение…» or no-op.

6.2 Enforce duration ≤90s using `message.voice.duration`.
**AC**: >90s → «Слишком длинное…»

## Chunk 7 — Audio Download & Convert

7.1 Implement Telegram file download: `getFile` → download by `file_path`.
**AC**: OGG/Opus file saved to temp dir.

7.2 Add ffmpeg invocation to convert to WAV PCM 16k mono.
**AC**: Output WAV exists; verify with `ffprobe`.

7.3 Clean temp files after use.
**AC**: No temp leak across runs.

## Chunk 8 — ElevenLabs Transcribe

8.1 Implement client: `transcribe(file: File, language="ru") : String`.
**AC**: Returns text for known sample.

8.2 Add retry-once policy for 5xx/timeouts.
**AC**: Simulated failure triggers retry.

8.3 Integrate full pipeline and reply to the original message with text.
**AC**: End-to-end: voice → text reply.

## Chunk 9 — Error Notifications

9.1 Global exception handler: DM admin with stack traces.
**AC**: Forced throw in handler triggers DM.

9.2 ElevenLabs permanent failure → DM admin + user-friendly error.
**AC**: Test with invalid key.

## Chunk 10 — Metrics & Graceful Shutdown

10.1 In-memory counters: `totalRequests`, `totalFailures`, `avgTranscriptionMs` (EWMA or simple avg).
**AC**: Values change after requests.

10.2 `/metrics` (admin-only) returns JSON stats.
**AC**: Curl returns stats.

10.3 Graceful shutdown: stop intake, await in-flight jobs (with timeout).
**AC**: SIGTERM drains then exits.

## Chunk 11 — Backups & Retention

11.1 Add backup sidecar (cron) or host cron that runs `sqlite3 db .dump > /backups/db-YYYYmmdd.sql`.
**AC**: File appears daily.

11.2 Retention script: delete backups older than 7 days.
**AC**: After 8 days, first dump removed.

11.3 Document restore steps.
**AC**: Dry-run restore in staging.

## Chunk 12 — CI/CD & Prod Deploy

12.1 GitHub Actions: build image on push; tag `latest` for staging.
**AC**: Image available in registry.

12.2 Makefile: `make deploy-staging` runs SSH → compose pull/up.
**AC**: One command updates staging.

12.3 On tag `vX.Y.Z`, build & push `bot:vX.Y.Z`; prod deploy is manual `make deploy-prod`.
**AC**: Prod updated after tag & deploy.

12.4 Caddy TLS & webhook set: configure domain; set Telegram webhook with secret.
**AC**: Telegram confirms webhook; messages arrive.

---

# Part D — Third Pass: Micro-Steps (5–30 min, Commit-Level Tasks)

> Example for **Chunks 0–3** (continue similarly for others)

### Chunk 0 Micro-Steps

* **c0-1**: `gradle init` Kotlin app; add Ktor deps. *DoD*: `./gradlew run` prints hello.
* **c0-2**: Create `Application.kt` with Ktor engine; add `/` → 200 OK. *DoD*: curl returns OK.
* **c0-3**: Add `Dockerfile` (multi-stage: build with Gradle, run with JRE). *DoD*: `docker run` prints.
* **c0-4**: Add `docker-compose.staging.yml` with `bot` service (ports 8080), `caddy` (80/443), volumes. *DoD*: both services start.
* **c0-5**: Add `.env.example` and config loader; log masked envs. *DoD*: app logs which vars are missing.

### Chunk 1 Micro-Steps

* **c1-1**: Add `ADMIN_HTTP_TOKEN` env; middleware to check header for `/health`. *DoD*: unauthorized returns 401.
* **c1-2**: Add uptime tracker. *DoD*: JSON `uptimeSeconds` increases.

### Chunk 2 Micro-Steps

* **c2-1**: Add `WEBHOOK_PATH`, mount route. *DoD*: only that path responds.
* **c2-2**: Add Telegram secret token header check. *DoD*: header mismatch → 401.
* **c2-3**: Add DTO or pengrad `Update` parsing; log message type. *DoD*: log shows receipt.

### Chunk 3 Micro-Steps

* **c3-1**: Wire Telegram sendMessage client (pengrad). *DoD*: send a test message to admin.
* **c3-2**: `/start` handler (authorized/unauthorized text). *DoD*: different responses based on auth flag (temp).
* **c3-3**: `/help` text. *DoD*: static RU guidance.
* **c3-4**: Default non-admin unknown → «Эта команда не поддерживается». *DoD*: verified.

*(Repeat micro-step style for Chunks 4–12; each 5–30 minutes, 1–2 files changed, small test.)*

---

# Part E — Implementation Details & Decisions

## Libraries & Versions

* **Ktor**: server-netty, content negotiation (Jackson or Kotlinx JSON).
* **Telegram**: `com.github.pengrad:java-telegram-bot-api` (lightweight, stable).
* **DB**: `org.xerial:sqlite-jdbc` + JetBrains **Exposed** (DAO or DSL) for simplicity.
* **HTTP client**: OkHttp or Ktor client (for Telegram File & ElevenLabs APIs).
* **Coroutines**: Kotlin coroutines for async pipeline.
* **FFmpeg**: call process via Runtime exec; validate version at startup.

## Process Model

* **Webhook** handler parses update → dispatches:

  * admin commands (if `from.id == ADMIN_ID`)
  * user commands/voices (if user active)
* Voice job steps: download → convert → transcribe → reply → cleanup.
* **Retry**: ElevenLabs call wrapped with one retry on timeout/5xx.
* **Errors**: Any exception → DM admin with stack trace; user gets RU error for their message, if applicable.
* **Shutdown**: Stop accepting requests; wait for active jobs with timeout (e.g., 30s).

## Config (Env Vars)

* `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ADMIN_ID`, `WEBHOOK_PATH`, `WEBHOOK_SECRET`, `ADMIN_HTTP_TOKEN`
* `ELEVENLABS_API_KEY`
* `ENV` (`staging`/`prod`), `PORT` (default 8080)

## Data Model

* **users**: `telegram_user_id (PK)`, `username`, `status (active|blocked)`, `added_by`, `added_at`
* **invites**: `token (PK)`, `created_by`, `created_at`, `expires_at (NULL ok)`, `used_by (NULL)`, `used_at (NULL)`

## Docker & Compose

* **bot**: built from Dockerfile; mounts `db_data` at `/app/data`.
* **caddy**: automatic TLS; Caddyfile to proxy `/:443` → `bot:8080` (limit to `/webhook/...`, `/health`, `/metrics`).
* Volumes: `db_data`, `caddy_data`, `backups`.

## Backups

* Simple cron container or host cron: `sqlite3 /app/data/bot.db .dump > /backups/db-$(date +%F).sql`.
* Retention script removes files older than 7 days.

## CI/CD

* GitHub Actions: build, tag `latest` on `main`; build tag `vX.Y.Z` for prod.
* Optional SSH deploy step or manual `make deploy-*` from VPS.

---

# Part F — Acceptance Criteria per Phase (Condensed)

* **Phase 1**: `/health` works locally via Compose with admin token.
* **Phase 2**: Telegram webhook endpoint responds with 200 & logs updates.
* **Phase 3**: Admin can `/list`, `/allow`, `/deny`; invites work.
* **Phase 4**: Voice → transcript reply within acceptable time; >90s rejected.
* **Phase 5**: Metrics show traffic; graceful shutdown drains; nightly backups appear; prod webhook receives messages via HTTPS.

---

# Part G — Risk Log & Mitigations

* **FFmpeg inside container**: Ensure base image with ffmpeg; add health check on startup (run `ffmpeg -version`).
* **ElevenLabs API changes/quotas**: Configurable timeout/retry; DM admin on quota errors; feature flag to temporarily block transcriptions if hitting quota.
* **Telegram webhook misconfig**: Add startup check that verifies current webhook info; log if mismatch.
* **DB locks**: Single-writer patterns; small transactions; close resources; use WAL mode.

---

# Part H — Example File Stubs (Names Only)

* `src/main/kotlin/config/AppConfig.kt`
* `src/main/kotlin/bot/WebhookRoutes.kt`
* `src/main/kotlin/bot/TelegramClient.kt`
* `src/main/kotlin/admin/AdminCommands.kt`
* `src/main/kotlin/db/Database.kt`, `db/UsersDao.kt`, `db/InvitesDao.kt`
* `src/main/kotlin/elevenlabs/TranscribeClient.kt`
* `src/main/kotlin/util/Audio.kt`, `util/Errors.kt`, `util/Metrics.kt`
* `Dockerfile`, `docker-compose.staging.yml`, `docker-compose.prod.yml`, `Caddyfile`
* `Makefile`, `.env.example`, `.github/workflows/ci.yml`

---

# Part I — Next Steps (Actionable To-Do Start)

1. Do **Chunk 0** micro-steps c0-1..c0-5 (2–3 hours).
2. Finish **Chunk 1–2** (1–2 hours). You’ll have a verified webhook receiver with admin-only health.
3. Decide DB layer (Exposed vs JDBC DSL); implement **Chunk 4** (2–3 hours).
4. Implement **Chunk 6–8** for full voice pipeline (1 day).
5. Add ops: **Chunks 9–12** (0.5–1 day).

This plan balances safety (small steps, verifiable) with momentum (each chunk delivers value).
