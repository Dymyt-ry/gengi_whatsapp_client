# BBWA — BlackBerry WhatsApp Client

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/Android-API%2018%2B-blue.svg)](https://developer.android.com/about/versions/android-4.3)
[![Evolution API](https://img.shields.io/badge/Evolution%20API-v2-orange.svg)](https://github.com/EvolutionAPI/evolution-api)

A thin native Android client for WhatsApp on **legacy Android devices** (API 18+), including **BlackBerry 10** devices running the Android runtime. Uses [Evolution API v2](https://github.com/EvolutionAPI/evolution-api) as the WhatsApp gateway and a lightweight Node.js backend as a webhook-populated message cache.

> Built by [WebFlex](https://webflex.cz) — *WhatsApp for legacy Android, BlackBerry, Evolution API v2.*

---

## Features

- **LID resolution** — resolves `@lid` pseudonyms to real phone numbers via Evolution API
- **Unread counters** — green badge on chat list, cleared on open
- **Push notifications** — Android background polling with notify flags
- **Group names** — fetched from Evolution API on first group message
- **Message reactions** — emoji reactions shown below messages
- **Chat renaming** — custom display names persisted in backend cache
- **AMOLED black UI** — Holo Dark theme, pure native Android (no WebView, no AppCompat)

---

## Architecture

```
WhatsApp ──► Evolution API v2 ──► POST /webhook
                                        │
                              Node.js backend (cache)
                                        │
                              GET /chats, /chat/:id
                                        │
                              Android app (API 18, polling)
```

- **Backend** (`/backend`): Node.js/Express server. Webhook from Evolution API populates an in-memory message cache. Not a pass-through proxy — the Android app reads from the cache.
- **Android** (`/android`): Native Java app targeting API 18. Pure ListView-based UI with Holo Dark theme. Polls the backend every 4 seconds.

---

## Requirements

- **Node.js 18+** (backend)
- **Android SDK** with API 18 platform (build)
- **Evolution API v2** instance with a connected WhatsApp session
- **Docker / Coolify** (optional, for backend deployment)

---

## Installation

### Backend

```bash
git clone https://github.com/your-org/bbwa.git
cd bbwa/backend
cp .env.example .env
# Edit .env with your values
npm install
npm start
```

#### Docker / Coolify

Deploy the `backend/` directory as a Node.js container. Set the environment variables listed below. Point your Evolution API webhook to `https://your-backend-url/webhook`.

### Android APK

```bash
cd android
./gradlew assembleRelease
```

APK output: `android/app/build/outputs/apk/release/`

Sideload the APK to your device. On first launch, enter your backend URL and API token in Settings.

---

## Environment Variables

| Variable | Description | Example |
|---|---|---|
| `EVO_API_URL` | Evolution API base URL | `http://your-evo-host:8081` |
| `EVO_INSTANCE_NAME` | Evolution API instance name | `my-instance` |
| `EVO_API_KEY` | Evolution API key | `your-api-key` |
| `AUTH_TOKEN` | Token the Android app uses to authenticate | `your-secret-token` |
| `PORT` | Backend HTTP port (default: 3000) | `3000` |

---

## API Contract

All endpoints except `/webhook` and `/status` require the `x-api-token` header.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/status` | No | Health check |
| GET | `/chats` | Yes | List all conversations |
| GET | `/chat/:id` | Yes | Get messages for a chat (clears unread count) |
| POST | `/send` | Yes | Send message `{ "chatId": "...", "text": "..." }` |
| POST | `/webhook` | No | Evolution API webhook receiver |
| POST | `/chat/:id/rename` | Yes | Rename a chat `{ "name": "..." }` |

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feat/my-feature`)
3. Commit your changes
4. Open a Pull Request

---

## License

MIT — Copyright (c) 2026 [WebFlex](https://webflex.cz)
