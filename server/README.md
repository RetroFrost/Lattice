# Lattice Server

Lattice Server is the first-party backend for the Lattice messenger. It has no Telegram dependency and requires no phone number or SMS provider.

## What exists now

- Username/password registration and login
- Argon2id password hashing
- Signed JWT sessions
- Private conversations, groups, and channels
- Channel posting permissions
- PostgreSQL message history
- Idempotent message sends via `client_id`
- WebSocket live-delivery events
- Public-key bundle and one-time-prekey storage for client-side E2EE
- Opaque ciphertext message storage: the API does not require plaintext message bodies
- Attachment metadata table ready for an encrypted object-storage layer

## Run locally

```bash
docker compose -f server/compose.yml up --build
```

The API will listen on `http://localhost:8080`.

## Environment

- `DATABASE_URL` — PostgreSQL connection string
- `JWT_SECRET` — at least 32 characters; use a random secret in production
- `LISTEN_ADDR` — optional, defaults to `:8080`

## API sketch

### Accounts

`POST /v1/auth/register`

```json
{"username":"ethan","password":"a-long-password","display_name":"Ethan"}
```

`POST /v1/auth/login`

```json
{"username":"ethan","password":"a-long-password"}
```

Both return a bearer token. Protected endpoints require:

```text
Authorization: Bearer <token>
```

### Conversations

`POST /v1/conversations`

```json
{"kind":"group","title":"Test group","members":["alice","bob"]}
```

Kinds are `private`, `group`, and `channel`.

`GET /v1/conversations`

### Messages

`POST /v1/conversations/{conversationId}/messages`

```json
{
  "client_id":"local-unique-id",
  "ciphertext":"base64-or-armored-client-ciphertext",
  "envelope":{"version":1,"scheme":"lattice-e2ee-v1"}
}
```

`GET /v1/conversations/{conversationId}/messages?after=123`

The server treats `ciphertext` and `envelope` as opaque client data. A production Lattice client must encrypt before upload; merely using these fields does not by itself provide E2EE.

### Key bundles

`PUT /v1/keys` publishes an identity key, signed pre-key, its signature, and a pool of one-time pre-keys.

`GET /v1/keys/{username}` returns the public bundle and atomically consumes at most one one-time pre-key.

These endpoints are intended to support a reviewed client-side protocol such as libsignal. Lattice should not invent its own cryptographic ratchet.

### Realtime

Connect to `GET /v1/ws` with the normal bearer `Authorization` header. New-message events have type `message.new`. Messages are still written through the REST endpoint so retries can remain idempotent.

## Production notes

The development Compose stack is intentionally simple. Before public deployment, add TLS at the edge, managed PostgreSQL backups, rate limiting, abuse controls, account recovery, device/session management, encrypted attachment storage, observability that avoids message contents, and independent security review of the client E2EE implementation.
