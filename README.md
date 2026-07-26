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
- `data/` - Room DB, Retrofit API, Socket.IO client, repositories, DTOs
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
4. **Chat** - Real-time messaging with Socket.IO
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
| POST | `/api/auth/refresh` | Refresh token |
| GET | `/api/servers` | List servers |
| POST | `/api/servers` | Create server |
| DELETE | `/api/servers/:id` | Delete server |
| POST | `/api/servers/join` | Join via invite |
| POST | `/api/servers/:id/leave` | Leave server |
| GET | `/api/servers/:id/channels` | List channels |
| POST | `/api/servers/:id/channels` | Create channel |
| DELETE | `/api/channels/:id` | Delete channel |
| GET | `/api/channels/:id/messages` | Get messages |
| POST | `/api/messages` | Send message |

## WebSocket Events
- `join_channel(channelId)`
- `leave_channel(channelId)`
- `send_message(channelId, content)`
- `message:receive` (server -> client)

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
cd Bunny/docker
docker-compose up
```

### Android App
1. Open `Bunny` folder in Android Studio
2. Update `Constants.BASE_URL` in `util/Constants.kt` to match backend IP
3. Run on Android emulator or device (API 26+)

## Notes
- Mobile-only (Android)
- Dark theme as default
- Offline caching with Room
- JWT with short-lived access + refresh tokens
- Password hashing with bcrypt
- Scalable architecture (MVVM + Repository pattern)
