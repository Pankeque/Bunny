# Bunny

Mobile-first communication platform MVP.

## APK Download

A instalação por APK é recomendada para testes e uso sem Google Play.

### Pré-requisitos
- Android 8.0 (API 26) ou superior
- Permitir instalação de apps de fontes desconhecidas nas **Configurações > Segurança**

### Instalação
1. Baixe o APK mais recente na seção [Releases](https://github.com/Pankeque/Bunny/releases).
2. Abra o arquivo `.apk` no dispositivo.
3. Toque em **Instalar** e aguarde.

### Publicando uma nova release
Crie e envie uma tag semântica para disparar o workflow de release:
```bash
git tag v1.0.0
git push origin v1.0.0
```
O GitHub Actions compila o APK release assinado e cria automaticamente a release com o APK anexado. `versionName` é derivado da tag e `versionCode` é incrementado a cada commit.

## Building from Source

- Linux/macOS: `./gradlew assembleDebug`
- Windows: `.\gradlew.bat assembleDebug`

Make sure you have JDK 17+ installed.

## Screenshots

*Em breve...*

---

## Tech Stack

- **Frontend:** Kotlin + Jetpack Compose, MVVM, Retrofit, OkHttp WebSocket, Hilt, Room
- **Backend:** Ktor (Kotlin-native)
- **Database:** PostgreSQL
- **Real-Time:** OkHttp WebSocket + JSON channel-based pub/sub
- **Security:** TLS, bcrypt, rate limiting, EncryptedSharedPreferences

## Structure

### Android App (`Bunny/app/`)
- `data/` - Room DB, Retrofit API, WebSocket gateway client, repositories, DTOs
- `domain/` - Domain models and repository interfaces
- `ui/` - Compose screens (Login, Register, Servers, Channels, Chat, Profile)
- `di/` - Hilt DI module
- `util/` - Constants

### Backend (`Bunny/backend/`)
- `routes/` - REST endpoints (auth, servers, channels, messages)
- `plugins/` - Security, CORS, WebSockets, rate limiting, monitoring
- `service/` - Database queries via Exposed ORM
- `model/` - PostgreSQL tables
- `dto/` - Response serialization
- `config/` - Database connection

### Docker (`Bunny/docker/`)
- `Dockerfile` - Backend container
- `docker-compose.yml` - Postgres + Backend
- `init.sql` - PostgreSQL schema

## Screens
1. **Login/Register** - JWT-based authentication
2. **Server List** - Create, join, leave servers
3. **Channel List** - Create and delete text channels
4. **Chat** - Real-time messaging with OkHttp WebSocket gateway
5. **Profile** - User info and logout
6. **Profile Edit** - Edit username, avatar, theme
7. **Server Settings** - Rename server, update icon, manage roles, delete
8. **Channel Settings** - Rename channel, delete
9. **Role Management** - Create/delete custom roles per server

## Responsive Design

The app adapts seamlessly between portrait and landscape orientations:

### Portrait Mode
- Bottom navigation bar with Servers and Profile tabs
- Stacked navigation: Server List → Channel List → Chat
- Full-screen composable for each screen

### Landscape / Tablet Mode
- `NavigationRail` sidebar with Servers and Profile shortcuts
- Master-detail layout: server list as sidebar, main content area for channels/chat
- Side-by-side server list + channel list + chat view
- Adaptive `Modifier.fillMaxSize()` on all screens for proper scaling

### Orientation Detection
- `ResponsiveUtil` utility class detects orientation and tablet form factors
- `isMasterDetail()` returns `true` for landscape or tablet (≥600dp width)
- `MainActivity` uses `LocalConfiguration` to reactively update layout on rotation
- `BunnyNavHost(isMasterDetail = ...)` switches between portrait and landscape layouts

### Key Responsive Components
- `BunnyNavHost` - switches between `PortraitNavHost` and `MasterDetailNavHost`
- `PortraitNavHost` - bottom navigation + stacked screens
- `MasterDetailNavHost` - navigation rail + content area
- `MasterDetailLayout` - rail sidebar + adaptive content box
- All screens accept optional `Modifier` parameter for flexible sizing

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register user |
| POST | `/api/auth/login` | Login JWT |
| POST | `/api/auth/refresh` | Refresh token (`refresh_token` body) |
| GET | `/api/servers` | List servers |
| POST | `/api/servers` | Create server |
| PUT | `/api/servers/:id` | Update server |
| DELETE | `/api/servers/:id` | Delete server |
| POST | `/api/servers/join` | Join via invite |
| POST | `/api/servers/:id/leave` | Leave server |
| POST | `/api/servers/:id/icon` | Update server icon |
| POST | `/api/servers/:id/regenerate-invite` | Regenerate invite code |
| GET | `/api/servers/:id/channels` | List channels |
| POST | `/api/servers/:id/channels` | Create channel |
| GET | `/api/servers/:serverId/roles` | List roles |
| POST | `/api/servers/:serverId/roles` | Create role |
| DELETE | `/api/roles/:roleId` | Delete role |
| GET | `/api/channels/:id` | Get channel |
| PUT | `/api/channels/:id` | Update channel |
| DELETE | `/api/channels/:id` | Delete channel |
| GET | `/api/channels/:channelId/messages` | Get messages |
| POST | `/api/channels/:channelId/messages` | Send message (`content` body) |
| PUT | `/api/users/me` | Update profile |

All request/response bodies use snake_case JSON (e.g. `channel_id`, `access_token`).

## WebSocket Gateway (Discord-style protocol)

The client talks to the gateway with a JSON envelope `{ "op": ..., "type": ..., "data": ... }`.

Client -> Server ops:
- `join_channel(channelId)` - subscribe to a channel
- `leave_channel(channelId)` - unsubscribe from a channel
- `send_message(channelId, content, nonce)` - send a message (nonce dedupes retransmissions)
- `heartbeat` - keepalive, expects `heartbeat_ack`

Server -> Client:
- `ready(userId)` - handshake complete
- `hello(heartbeatInterval)` - start heartbeat cadence
- `heartbeat_ack` - liveness confirmation
- `event:message_received(messageId, channelId, userId, content, nonce, sequence, timestamp)` - new message (echoed to sender too)
- `event:presence_update(channelId, userId, online)` / `presence_update(channelId, users)` - presence snapshot & changes
- `event:error(code, error)` - gateway error (e.g. `forbidden`, `invalid_message`)

The app auto-reconnects with exponential backoff, rejoins channels, replays queued sends,
and resumes the heartbeat on the new connection (like Discord's gateway resume).

## Database Schema
- `users` - username, password_hash, avatar
- `servers` - name, icon, owner, invite_code
- `server_members` - user-server membership
- `channels` - text channels within servers
- `messages` - real-time messages
- `refresh_tokens` - secure session management

## Running

### Backend + Postgres
```bash
# a partir da raiz do repositório
docker compose -f docker/docker-compose.yml up --build
```

### Android App
1. Open `Bunny` folder in Android Studio
2. Run on Android emulator or device (API 26+)

## Backend Connectivity

Like Discord, the app connects to fixed, stable endpoints defined at build time in
`app/src/main/java/com/bunny/util/Constants.kt`:

- `BASE_URL` — REST API (`https://bunny-backend-lq2l.onrender.com/`)
- `SOCKET_URL` — realtime WebSocket gateway (`wss://bunny-backend-lq2l.onrender.com`)

Users never configure IP addresses. The operator deploys the backend and, if needed,
changes the two constants before building the APK.

### Deploying the backend on Render

1. Create a web service from this repository (root `Dockerfile`).
2. Create a **managed PostgreSQL** instance on Render.
3. Attach the database to the web service (Render then auto-injects
   `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` and `DATABASE_URL`).
4. In the web service **Environment** tab, add:
   - `DATABASE_URL` = the Internal Database URL from the Postgres instance
   - `DATABASE_USER` / `DATABASE_PASSWORD` = Postgres credentials
   - `JWT_SECRET` = a long random string
5. Redeploy. The backend reads the database config from these env vars and
   auto-creates the schema on first boot.

## Notes
- Mobile-only (Android)
- Dark theme as default
- Offline caching with Room
- JWT with short-lived access + refresh tokens
- Password hashing with bcrypt
- Scalable architecture (MVVM + Repository pattern)
