# TCP-Server

Post-quantum encrypted messaging server with end-to-end encryption, voice/video call relay, and push notifications.

## Features

- **Post-Quantum Handshake** — ML-KEM-768 + X25519 hybrid key exchange, ML-DSA-65 server authentication (via TCPSecure protocol)
- **Session Resumption** — Stateless encrypted tickets (AES-256-GCM)
- **E2E Encrypted Messaging** — Server relays encrypted content, never reads plaintext
- **Voice & Video Calls** — UDP relay for encrypted audio (Opus) and video (H.264) streams
- **FCM Push Notifications** — Offline message alerts and incoming call push (OAuth2 JWT, zero external dependencies)
- **Chat Invites** — Invite system with accept/deny before key exchange
- **User Profiles** — Profile picture upload and storage
- **Account Management** — Email confirmation, forgot/reset password, account deletion with full cascade
- **Security** — Bcrypt + HMAC pepper passwords, rate limiting, IP logging, IDOR protection
- **Scheduled Cleanup** — Expired invites (7d), old logs (7d), unconfirmed accounts (1d)

## Tech Stack

- Java 21 (Virtual Threads)
- PostgreSQL + HikariCP
- TCPSecure 1.1.1 (custom post-quantum TLS-like protocol)
- BouncyCastle (cryptography)
- Gson (serialization)
- Dotenv (configuration)

## Setup

1. **Clone and configure:**
   ```bash
   git clone https://github.com/Rares-boop/TCP-Server-updated.git
   cd TCP-Server-updated
   cp .env.example .env
   ```

2. **Edit `.env`:**
   ```env
   DB_URL=jdbc:postgresql://localhost:5432/your_db
   DB_USER=your_user
   DB_PASSWORD=your_password
   TCP_PORT=15555
   UDP_AUDIO_PORT=15556
   UDP_VIDEO_PORT=15557
   DILITHIUM_KEY_PATH=server_dilithium.enc
   DILITHIUM_KEY_PASSWORD=your_key_password
   SMTP_EMAIL=your_email@gmail.com
   SMTP_PASSWORD=your_app_password
   FCM_SERVICE_ACCOUNT_PATH=firebase-service-account.json
   ```

3. **Database:**
   ```bash
   # Tables are auto-created on first run via DatabaseInitializer
   # Or run manually:
   java -cp "TCP_Server.jar:lib/*" app.database.DatabaseInitializer
   ```

4. **Firebase (for push notifications):**
    - Create a project at [Firebase Console](https://console.firebase.google.com/)
    - Download service account JSON → place as `firebase-service-account.json`

5. **Run:**
   ```bash
   java -cp "TCP_Server.jar:lib/*" app.Program
   ```

## Database Schema

| Table | Purpose |
|-------|---------|
| `USERS` | Accounts, keys, FCM tokens, profile pictures |
| `GROUP_CHATS` | Chat rooms |
| `GROUP_MEMBERS` | Chat membership |
| `MESSAGES` | Encrypted message storage |
| `OFFLINE_QUEUE` | Pending packets for offline users |
| `USER_LOGS` | Login attempts, rate limiting |
| `CHAT_INVITES` | Pending/accepted/denied chat invitations |

## Protocol

Communication uses a custom JSON-over-TCP protocol secured by TCPSecure. Each packet:
```json
{"type": "PACKET_TYPE", "senderId": 1, "payload": {...}}
```

~45 packet types covering auth, messaging, calls, profiles, and invitations.

