# BBWA — BlackBerry WhatsApp Client

A thin native Android client for WhatsApp on legacy Android devices (API 18+), including BlackBerry 10 devices running the Android runtime. Uses Evolution API v2 as the WhatsApp gateway and a lightweight Node.js backend as a webhook-populated message cache.

---

## Features

- **🖼️ Full Image Support** — send and receive image messages. Tap an image for a full-screen view, or long-press to save it to the Gallery. Heavy lifting (Base64 decoding/encoding) is offloaded to the backend to prevent OOM crashes on legacy devices.
- **❤️ Two-Way Reactions** — double-tap any message to instantly send a ❤️, or long-press for a full emoji selection menu.
- **⚡ Extreme Performance** — built for devices with 1-2GB RAM. Zero-allocation in-place UI updates, reverse-search cache lookups, and strict scroll-state retention.
- **👤 Contact Aliases** — locally rename contacts directly from the chat menu.
- **LID resolution** — resolves `@lid` pseudonyms to real phone numbers via Evolution API v2.
- **Unread counters** — green badge on chat list, cleared on open.
- **Push notifications** — Android background polling with vibration.
- **Group support** — group names fetched from Evolution API.
- **AMOLED black UI** — Holo Dark theme, pure native Android (no WebView, no AppCompat).
- **Settings screen** — configure backend URL and API token on first launch.

---

## Why Evolution API v2?

WhatsApp recently started using `@lid` (Local ID) identifiers instead of real phone numbers for contacts — especially for unsaved contacts and multi-device sessions. This caused `406 Not Acceptable` errors when replying to messages using Evolution API v1.x.

Evolution API v2 resolves this natively using `participantAlt` and `remoteJidAlt` fields, ensuring BBWA always works with real phone numbers. **Do not use v1.x with this project.**

---

## Architecture
```
WhatsApp ──► Evolution API v2 ──► POST /webhook
                                        │
                                 Node.js backend (cache)
                                        │
                              GET /chats, /chat/:id
                              GET /api/media/:id
                                        │
                          Android app (API 18, polling)
```

- **Backend (`/backend`)**: Node.js/Express server. Webhook from Evolution API populates an in-memory message cache. Handles media formatting and payload translations to save client RAM.
- **Android (`/android`)**: Native Java app targeting API 18. Pure ListView-based UI. Polls backend every 4 seconds.

---

## Requirements

- Node.js 18+
- Android SDK with API 18 platform (for building APK)
- Evolution API v2 instance
- Docker / Coolify (optional, for backend deployment)

---

## Installation

### Backend
```bash
git clone https://github.com/your-username/bbwa.git
cd bbwa/backend
cp .env.example .env
# Edit .env with your values
npm install
npm start
```

### Docker / Coolify

Deploy the `backend/` directory as a Node.js app. Set the environment variables listed below. After deployment, register the Evolution API webhook:
```bash
curl -s -X POST http://YOUR_EVO_HOST:8081/webhook/set/YOUR_INSTANCE \
  -H "apikey: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"webhook":{"url":"https://your-backend-url/webhook","enabled":true,"events":["MESSAGES_UPSERT"],"webhookByEvents":false,"webhookBase64":false}}'
```

> ⚠️ Evolution API v2 uses a different webhook format than v1. Use the `webhook` object wrapper as shown above.

### Android APK
```bash
cd android
./gradlew assembleDebug
```

APK output: `android/app/build/outputs/apk/debug/`

Sideload the APK to your device. On first launch, enter your backend URL and API token in Settings.

---

## Environment Variables

| Variable | Description | Example |
|---|---|---|
| `EVO_API_URL` | Evolution API base URL | `http://your-evo-host:8081` |
| `EVO_INSTANCE_NAME` | Evolution API instance name | `myinstance` |
| `EVO_API_KEY` | Evolution API key | `your-api-key` |
| `AUTH_TOKEN` | Token for Android app auth | `your-secret-token` |
| `PORT` | Backend HTTP port | `3000` |

> ⚠️ **Do not use special characters** (`#`, `$`, `!`) in `AUTH_TOKEN` — Docker/Coolify may truncate the value at these characters. Use only alphanumeric characters.

---

## Evolution API v2 Setup

### 1. Deploy Evolution API v2

Use Docker image `atendai/evolution-api:v2.1.1`. Required environment variables:
```
SERVER_URL=http://your-server:8081
AUTHENTICATION_API_KEY=your-api-key
DATABASE_ENABLED=true
DATABASE_PROVIDER=postgresql
DATABASE_CONNECTION_URI=postgresql://user:pass@host:5432/evolution
CACHE_REDIS_ENABLED=false
CACHE_LOCAL_ENABLED=true
```

> ⚠️ **Critical:** Set `CACHE_REDIS_ENABLED=false` unless you have a dedicated Redis instance. Without it, the Baileys engine will boot-loop every 5 seconds and never generate a pairing code.

### 2. Create a WhatsApp Instance
```bash
curl -s -X POST http://YOUR_EVO_HOST:8081/instance/create \
  -H "apikey: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"instanceName":"myinstance","qrcode":true,"integration":"WHATSAPP-BAILEYS"}'
```

### 3. Get Pairing Code

Wait 10-15 seconds after creating the instance, then:
```bash
curl -s "http://YOUR_EVO_HOST:8081/instance/connect/myinstance?number=YOUR_PHONE_NUMBER" \
  -H "apikey: YOUR_API_KEY"
```

Response:
```json
{
  "pairingCode": "XXXXXXXX",
  "count": 1
}
```

On your phone: **WhatsApp → Linked Devices → Link a Device → Link with phone number** → enter the 8-digit code.

> ⚠️ `YOUR_PHONE_NUMBER` must include country code without `+`, e.g. `447911123456` for UK or `12025551234` for US.

---

## API Contract

All endpoints except `/webhook` and `/status` require the `x-api-token` header.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/status` | No | Health check |
| GET | `/chats` | Yes | List all conversations |
| GET | `/chat/:id` | Yes | Get messages for a chat |
| GET | `/api/media/:messageId` | Yes | Fetch raw image bytes from Evolution API |
| POST | `/send` | Yes | Send text message `{ "chatId": "...", "text": "..." }` |
| POST | `/api/messages/sendMedia` | Yes | Upload multipart/form-data image to WhatsApp |
| POST | `/api/messages/reaction` | Yes | Send reaction `{ "chatId": "...", "messageId": "...", "emoji": "❤️" }` |
| POST | `/webhook` | No | Evolution API webhook receiver |

---

## Troubleshooting

### `{"count": 0}` when getting pairing code
The Baileys engine is still initializing. Wait 10-15 seconds after creating the instance and try again. If it persists, check that `CACHE_REDIS_ENABLED=false` is set.

### Boot loop in Evolution API logs
```
Browser: Evolution API,Chrome,...
Baileys version env: 2,3000,...
Group Ignore: false
```
This means Redis is expected but not available. Set `CACHE_REDIS_ENABLED=false` and restart the container.

### `AUTH_TOKEN` getting truncated (Unauthorized errors)
Docker and Coolify treat `#` and `$` as special characters. Use only alphanumeric characters in `AUTH_TOKEN`.

### `not-acceptable` error when sending messages
This happens with Evolution API v1.x and `@lid` contacts. Upgrade to Evolution API v2.x.

### SSL handshake failure on BB10
BB10's Android runtime only supports older TLS versions. The app includes a custom `SSLSocketFactory` that enables TLS 1.0/1.1 compatibility. If you still get SSL errors, verify your backend certificate is valid.

### Black screen / "Chat not synced" on first launch
Open Settings (menu button → Settings) and enter your backend URL and API token. URL format: `https://your-backend-url` (no trailing slash).

### DNS resolution failures in Docker containers
Add DNS to your Docker daemon config (`/etc/docker/daemon.json`):
```json
{
  "dns": ["8.8.8.8", "1.1.1.1"]
}
```
Then restart Docker: `systemctl restart docker`

### Evolution API can't reach other services
Each Docker container needs to be on the same network. Connect containers manually:
```bash
docker network connect coolify your-container-name
```

### Evolution API v2 webhook format
v2 uses a different webhook registration format than v1. Use the `webhook` object wrapper:
```bash
-d '{"webhook":{"url":"...","enabled":true,"events":["MESSAGES_UPSERT"],"webhookByEvents":false,"webhookBase64":false}}'
```

---

## Building from Source

### Prerequisites
- JDK 8 (`brew install --cask temurin@8` on macOS)
- Android Studio with API 18 SDK installed
- Gradle 5.6.4

### Configure Android Studio JDK
File → Project Structure → SDK Location → JDK Location:
- macOS: `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home`

### Build APK
```bash
cd android
./gradlew assembleDebug
```

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feat/my-feature`)
3. Commit your changes
4. Open a Pull Request

---

## License

MIT — Copyright (c) 2026 BBWA Contributors
