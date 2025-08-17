# TODO — Telegram Voice Bot (Developer Checklist)

Use this as a step-by-step checklist. Each box should be checkable in a single work session (15–90 minutes). Adjust as needed.

---

## 0) Prerequisites & Accounts

* [ ] Get **Telegram Bot Token** (via @BotFather)
* [ ] Note **Telegram Admin ID** (your user ID)
* [ ] Register **domain** for webhook (e.g., `example.com`)
* [ ] Point DNS **A record** to VPS public IP
* [ ] Create **ElevenLabs API Key**
* [ ] VPS ready with **Docker 27+** and **Docker Compose v2**
* [ ] Ensure `ffmpeg` is available (host or container)

---

## 1) Repository & Project Bootstrap

* [ ] Initialize **Git** repo with `.gitignore` (Kotlin/Gradle/IntelliJ, Docker)
* [ ] Create **Gradle Kotlin JVM** project (JDK 21)
* [ ] Add dependencies:

  * [ ] Ktor: `server-netty`, `content-negotiation`, JSON (Jackson or Kotlinx)
  * [ ] Telegram API client: `com.github.pengrad:java-telegram-bot-api`
  * [ ] DB: `org.xerial:sqlite-jdbc` + JetBrains **Exposed** (or JDBC + DSL)
  * [ ] HTTP client: OkHttp or Ktor client
  * [ ] Coroutines: `kotlinx-coroutines-core`
  * [ ] Logging: `slf4j` + simple logger
* [ ] Add `.editorconfig` (spaces, UTF-8, newline at EOF)
* [ ] Add `README.md` (overview + quick start)

---

## 2) Configuration & Env Vars

* [ ] Define required env vars and document in `.env.example`:

  * [ ] `TELEGRAM_BOT_TOKEN`
  * [ ] `TELEGRAM_ADMIN_ID`
  * [ ] `ELEVENLABS_API_KEY`
  * [ ] `WEBHOOK_PATH` (random string)
  * [ ] `WEBHOOK_SECRET` (long random secret)
  * [ ] `ADMIN_HTTP_TOKEN` (for /health, /metrics)
  * [ ] `ENV` (staging|prod)
  * [ ] `PORT` (default 8080)
* [ ] Implement config loader; **mask secrets** in logs
* [ ] On startup, **validate presence** of required vars; exit with clear error if missing

---

## 3) Dockerization

* [ ] Create **multi-stage Dockerfile**

  * [ ] Build stage: Gradle build
  * [ ] Runtime stage: JRE 21 minimal base
  * [ ] Include `ffmpeg` in runtime image (or bind-mount host `ffmpeg`)
  * [ ] Run as **non-root** user
* [ ] Create `docker-compose.staging.yml`

  * [ ] Services: `bot`, `caddy`
  * [ ] Volumes: `db_data`, `caddy_data`, `backups`
  * [ ] Expose 80/443 on `caddy`
  * [ ] Healthchecks for `bot`
* [ ] Create `docker-compose.prod.yml` (copy from staging; adjust domains/labels)

---

## 4) Caddy Reverse Proxy & TLS

* [ ] Add `Caddyfile` with automatic HTTPS for domain
* [ ] Proxy to `bot:8080`
* [ ] Preserve and forward `X-Telegram-Bot-Api-Secret-Token`
* [ ] Limit allowed routes (at minimum: `/webhook/*`, `/health`, `/metrics`)
* [ ] Enable access/error logs
* [ ] Verify certificate issuance and renewal

---

## 5) Ktor Skeleton

* [ ] Create `Application.kt` with Netty engine
* [ ] Install **ContentNegotiation** + JSON
* [ ] Implement `/health` (admin-only via `X-Admin-Token`)

  * [ ] Return `{status, uptimeSeconds}` JSON
* [ ] Add `/metrics` (admin-only; placeholder JSON)
* [ ] Add global error handler (respond 500 JSON; will be replaced later with DM)

---

## 6) Telegram Webhook Receiver

* [ ] Add webhook route: `POST /webhook/{WEBHOOK_PATH}`
* [ ] Validate header `X-Telegram-Bot-Api-Secret-Token == WEBHOOK_SECRET` → otherwise 401
* [ ] Parse Telegram `Update` JSON (DTOs or pengrad models)
* [ ] Implement a minimal **sendMessage** helper (pengrad client)
* [ ] Log inbound update type (text, voice, command)

---

## 7) Basic Commands (RU)

* [ ] `/start` — welcome message; if unauthorized → "У вас нет доступа. Обратитесь к администратору."
* [ ] `/help` — brief usage: "Отправьте голосовое сообщение, и я верну текстовую расшифровку."
* [ ] Unknown command (non-admin) → "Эта команда не поддерживается"

---

## 8) SQLite & Migrations

* [ ] Initialize SQLite at `/app/data/bot.db`; enable WAL mode
* [ ] Create migrations for tables:

  * [ ] `users(telegram_user_id PK, username, status, added_by, added_at)`
  * [ ] `invites(token PK, created_by, created_at, expires_at NULL, used_by NULL, used_at NULL)`
* [ ] On startup: run migrations automatically
* [ ] Seed admin (from `TELEGRAM_ADMIN_ID`) as active

---

## 9) Admin Commands (Hidden from users)

* [ ] Restrict to admin by `from.id`
* [ ] `/list` — show users & status
* [ ] `/allow <username>` — add/activate user (store id/username on first use)
* [ ] `/deny <username>` — block user
* [ ] `/invite` — generate one-time token; DM admin with `/start <token>`
* [ ] Non-admin attempting any of these → "Эта команда не поддерживается"

---

## 10) Invite Flow

* [ ] Parse `/start <token>`
* [ ] Validate token (exists, unused, not expired)
* [ ] Create user record (active) with sender’s `id` and `username`
* [ ] Mark token used (`used_by`, `used_at`)
* [ ] Reply success message; allow usage immediately

---

## 11) Voice Intake & Validation

* [ ] Accept **private** chats only; ignore groups
* [ ] Ensure user is **active**; otherwise reply "У вас нет доступа"
* [ ] Accept only **voice messages**; ignore other types
* [ ] Enforce **≤90s** using `message.voice.duration`; reply if too long
* [ ] Prepare friendly RU error for non-voice content (optional)

---

## 12) Audio Download & Conversion

* [ ] Use Telegram `getFile` to resolve `file_path`
* [ ] Download OGG/Opus to temp dir (unique per request)
* [ ] Invoke `ffmpeg` to convert → WAV **PCM 16 kHz mono**
* [ ] Validate output exists and is non-empty
* [ ] Cleanup temp files on success/failure

---

## 13) ElevenLabs Transcription

* [ ] Implement client for ElevenLabs API (real API)
* [ ] Send WAV; set language to **Russian**
* [ ] Set reasonable timeouts (connect/read)
* [ ] **Retry once** on timeout/5xx
* [ ] On success: return transcript text
* [ ] On permanent failure: throw typed error

---

## 14) Reply Pipeline

* [ ] For each valid voice: run **download → convert → transcribe**
* [ ] Send transcript as a **reply to original voice message**
* [ ] If permanent failure: reply with RU error (e.g., "Не удалось расшифровать сообщение. Попробуйте позже.")

---

## 15) Error Notifications to Admin

* [ ] Global exception handler: DM admin with **full stack trace**
* [ ] On ElevenLabs permanent failure: DM admin with error + request metadata
* [ ] Ensure user receives a friendly RU error response when applicable

---

## 16) Metrics (Admin-Only)

* [ ] Track `totalRequests`, `totalFailures`
* [ ] Track average transcription time (simple avg or EWMA)
* [ ] Expose `/metrics` JSON (admin token required)

---

## 17) Graceful Shutdown

* [ ] Stop accepting new webhook requests on shutdown signal
* [ ] Wait for in-flight jobs (configurable timeout, e.g. 30s)
* [ ] Cancel and log any remaining tasks after timeout

---

## 18) Backups & Retention

* [ ] Create `backups` volume
* [ ] Add backup script: `sqlite3 /app/data/bot.db .dump > /backups/db-$(date +%F).sql`
* [ ] Schedule nightly via **cron sidecar** or **host cron**
* [ ] Retention script: delete files older than **7 days**
* [ ] Document **restore** procedure in README

---

## 19) CI/CD & Versioning

* [ ] Create GitHub Actions workflow:

  * [ ] On push to `main`: build & push image tagged `latest`
  * [ ] On tag `vX.Y.Z`: build & push image tagged `vX.Y.Z`
* [ ] Store registry credentials in repo secrets
* [ ] Add **Makefile**:

  * [ ] `make build-staging`, `make up-staging`, `make deploy-staging`
  * [ ] `make build-prod`, `make up-prod`, `make deploy-prod`
* [ ] Manual **prod** deploy step (SSH + compose pull/up)

---

## 20) Staging & Production Deployment

* [ ] Start **staging** stack: `docker compose -f docker-compose.staging.yml up -d`
* [ ] Verify **Caddy** serves HTTPS
* [ ] Set **Telegram webhook** with secret and full URL (`https://domain/WEBHOOK_PATH`)
* [ ] Send test updates; verify webhook logs
* [ ] Start **production** stack (after validation)
* [ ] Set production webhook similarly

---

## 21) Security Hardening

* [ ] Use **env vars** for all secrets (no secrets in Git)
* [ ] Use **random** `WEBHOOK_PATH` and strong `WEBHOOK_SECRET`
* [ ] Lock down firewall: allow only **22 (SSH)** and **443 (HTTPS)**
* [ ] SSH hardening: disable root login, use key auth
* [ ] Run Docker container **as non-root**; drop capabilities where possible
* [ ] Prefer **read-only** filesystem for bot container (if feasible)
* [ ] Keep base images and dependencies updated
* [ ] Ensure logs don’t print PII/transcripts

---

## 22) Maintenance Tasks

* [ ] Log rotation for Caddy and application logs
* [ ] Regularly `docker system prune` to clean old images/containers
* [ ] Monitor **disk usage** for `db_data` and `backups`
* [ ] Validate **TLS renewals** are functioning
* [ ] Periodically verify **webhook status** (Telegram `getWebhookInfo`)

---

## 23) Testing Checklist (Manual)

* [ ] `/health` with correct **admin token** → `{status: ok}`
* [ ] `/metrics` with admin token → counters visible
* [ ] Wrong admin token → **401** for both endpoints
* [ ] Unauthorized user `/start` → access denied (RU)
* [ ] Admin `/list` → shows admin
* [ ] Admin `/invite` → token generated; `/start <token>` by new user → authorized
* [ ] Admin `/allow` & `/deny` → toggles user access
* [ ] Authorized user sends **voice <90s** → receives transcript as **reply**
* [ ] Voice **>90s** → RU error about length
* [ ] Non-voice message → ignored or friendly RU guidance
* [ ] ElevenLabs failure (use bad key) → retry once, then RU error to user; DM admin with stack trace
* [ ] Graceful shutdown during active transcription → request completes, app exits
* [ ] Nightly backup created; after 8 days oldest backup pruned
* [ ] Restore from backup in staging succeeds

---

## 24) Release & Handover

* [ ] Tag first release `v1.0.0`
* [ ] Build & push image for tag
* [ ] Deploy to production via Make target
* [ ] Update README with final webhook URL and admin tokens
* [ ] Share credentials and ops notes securely
