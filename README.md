# TCP-Server

Post-quantum encrypted messaging server with end-to-end encryption, voice/video calls, and offline message delivery.

## Features

- **Post-Quantum Handshake** — Hybrid ML-KEM-768 (Kyber) + X25519 ECDH with ephemeral keys (PFS)
- **End-to-End Encryption** — AES-256-GCM transport encryption, Dilithium signatures for identity verification
- **Email Confirmation** — 6-digit code via SMTP (Gmail)
- **Password Security** — bcrypt (cost 12) with HMAC-SHA256 pepper
- **Rate Limiting** — 5 failed login attempts per IP / 15 minutes
- **IDOR Protection** — Membership and ownership checks on all operations
- **Voice/Video Relay** — Blind UDP relay (server never sees plaintext media)
- **Offline Queue** — Messages stored and delivered on reconnect
- **Virtual Threads** — Java 21+ lightweight concurrency (~20K concurrent connections)

## Project Structure

```
src/
├── app/
│   ├── Program.java              # Entry point
│   └── server/
│       ├── TcpServer.java        # TCP server + client handler
│       └── UdpServer.java        # UDP blind relay (audio/video)
├── database/
│   ├── DatabaseConnection.java   # HikariCP pool
│   ├── DatabaseInitializer.java  # Table creation
│   ├── chat/
│   │   ├── GroupChatRepository.java
│   │   └── MessageRepository.java
│   ├── queue/
│   │   └── OfflineQueueRepository.java
│   └── user/
│       ├── UserRepository.java
│       └── UserLogRepository.java
└── utils/
    ├── EmailUtils.java           # SMTP confirmation
    └── PasswordUtils.java        # bcrypt + pepper
```

## Setup

### Requirements

- Java 23+
- PostgreSQL

### Configuration

Create a `.env` file in the project root:

```env
DB_HOST=localhost
DB_PORT=5432
DB_DATABASE=tcpsecure
DB_USER=postgres
DB_PASSWORD=your_password
DB_POOL_SIZE=50

TCP_PORT=15555
UDP_AUDIO_PORT=15556
UDP_VIDEO_PORT=15557

PEPPER=your_secure_random_pepper

SMTP_EMAIL=your_email@gmail.com
SMTP_PASSWORD=your_app_password
```

### Database

Run `DatabaseInitializer.main()` to create all tables, or manually:

```sql
-- Tables: USERS, GROUP_CHATS, GROUP_MEMBERS, MESSAGES, OFFLINE_QUEUE, USER_LOGS
```

### Run

```bash
java -cp "out/production/TCP Server:lib/*" app.Program
```

### Docker

```bash
docker compose up -d
```