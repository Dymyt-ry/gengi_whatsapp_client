# BBWA — BlackBerry 10 WhatsApp Thin Client

## Architecture

Monorepo with two components:

- **`/backend`** — Node.js/Express server with an in-memory message cache populated by Evolution API webhooks. Not a pass-through proxy — the webhook populates the cache, and the Android app reads from it.
- **`/android`** — Native Android app (Java) targeting API 18 for BlackBerry 10 runtime compatibility. Pure native UI with Holo dark (AMOLED) theme.

## Key Constraints

- **Android API 18 hard limit** — no Java 8 features (no lambdas, no try-with-resources, no method references), no AndroidX, no AppCompat, no Material Design
- **Java source/target 1.6** — required for API 18 compatibility
- **OkHttp 3.12.12** — last version supporting Android 4.x/API 18
- **Holo dark theme only** — `android:Theme.Holo` with AMOLED black backgrounds
- **No WebView** — pure native UI with ListView, Activity (not Fragment-based)
- **Webhook-populated cache** — backend cache IS the data store, no TTL, lost on restart (by design)
- **Token auth** — `x-api-token` header on all authenticated endpoints

## Backend Commands

```bash
cd backend
npm install        # install dependencies
npm run dev        # start with nodemon (hot reload)
npm start          # start production server
```

Server runs on port 3000 by default. Copy `.env.example` to `.env` and fill in values.

## Android Commands

```bash
cd android
./gradlew assembleDebug    # build debug APK
./gradlew installDebug     # build and install on connected device
```

Requires Android SDK with API 18 platform installed. Package: `cz.webflex.bbwa`.

## API Contract (Backend <-> Android)

All endpoints except `/webhook` and `/status` require `x-api-token` header.

| Method | Endpoint       | Description                                   |
|--------|----------------|-----------------------------------------------|
| GET    | /status        | Health check                                  |
| GET    | /chats         | List all conversations (from cache)            |
| GET    | /chat/:id      | Get messages for a chat (from cache)           |
| POST   | /send          | Send message `{ "chatId": "...", "text": "..." }` |
| POST   | /webhook       | Evolution API webhook receiver                 |

## Environment Setup

1. Set up Evolution API v1.8.2 instance and connect a WhatsApp session
2. Copy `backend/.env.example` to `backend/.env` and configure:
   - `EVO_API_URL` — Evolution API base URL
   - `EVO_INSTANCE_NAME` — Evolution API instance name
   - `EVO_API_KEY` — Evolution API key
   - `AUTH_TOKEN` — token the Android app will use to authenticate
3. Configure the Android app's `ApiClient` with the backend URL and auth token
4. Point Evolution API webhook to `{backend_url}/webhook` for `messages.upsert` events
