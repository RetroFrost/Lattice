package app

import (
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/crypto/argon2"
)

type App struct {
	db        *pgxpool.Pool
	jwtSecret []byte
	hub       *hub
	upgrader  websocket.Upgrader
}

type claims struct {
	UserID   string `json:"uid"`
	Username string `json:"usr"`
	jwt.RegisteredClaims
}

type ctxKey string

const userContextKey ctxKey = "lattice-user"

var usernameRE = regexp.MustCompile(`^[a-z0-9_]{3,32}$`)

func New(ctx context.Context) (*App, error) {
	databaseURL := strings.TrimSpace(os.Getenv("DATABASE_URL"))
	if databaseURL == "" {
		return nil, errors.New("DATABASE_URL is required")
	}
	jwtSecret := strings.TrimSpace(os.Getenv("JWT_SECRET"))
	if len(jwtSecret) < 32 {
		return nil, errors.New("JWT_SECRET must contain at least 32 characters")
	}
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		return nil, fmt.Errorf("open database: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping database: %w", err)
	}
	return &App{
		db:        pool,
		jwtSecret: []byte(jwtSecret),
		hub:       newHub(),
		upgrader: websocket.Upgrader{
			ReadBufferSize:  4096,
			WriteBufferSize: 4096,
			CheckOrigin: func(r *http.Request) bool {
				// Native clients do not rely on browser Origin. A reverse proxy can
				// add a stricter policy for web clients later.
				return true
			},
		},
	}, nil
}

func (a *App) Close() { a.db.Close() }

func (a *App) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", a.health)
	mux.HandleFunc("POST /v1/auth/register", a.register)
	mux.HandleFunc("POST /v1/auth/login", a.login)
	mux.Handle("GET /v1/me", a.auth(http.HandlerFunc(a.me)))
	mux.Handle("GET /v1/conversations", a.auth(http.HandlerFunc(a.listConversations)))
	mux.Handle("POST /v1/conversations", a.auth(http.HandlerFunc(a.createConversation)))
	mux.Handle("GET /v1/conversations/{id}/messages", a.auth(http.HandlerFunc(a.listMessages)))
	mux.Handle("POST /v1/conversations/{id}/messages", a.auth(http.HandlerFunc(a.sendMessage)))
	mux.Handle("PUT /v1/keys", a.auth(http.HandlerFunc(a.putKeyBundle)))
	mux.Handle("GET /v1/keys/{username}", a.auth(http.HandlerFunc(a.getKeyBundle)))
	mux.Handle("GET /v1/ws", a.auth(http.HandlerFunc(a.websocket)))
	return requestLogger(mux)
}

func (a *App) health(w http.ResponseWriter, r *http.Request) {
	if err := a.db.Ping(r.Context()); err != nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]any{"ok": false})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "service": "lattice"})
}

func (a *App) register(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Username    string `json:"username"`
		Password    string `json:"password"`
		DisplayName string `json:"display_name"`
	}
	if !decodeJSON(w, r, &in) {
		return
	}
	in.Username = strings.ToLower(strings.TrimSpace(in.Username))
	in.DisplayName = strings.TrimSpace(in.DisplayName)
	if !usernameRE.MatchString(in.Username) {
		writeError(w, http.StatusBadRequest, "username must be 3-32 lowercase letters, numbers, or underscores")
		return
	}
	if len(in.Password) < 10 || len(in.Password) > 256 {
		writeError(w, http.StatusBadRequest, "password must be 10-256 characters")
		return
	}
	if in.DisplayName == "" {
		in.DisplayName = in.Username
	}
	if len(in.DisplayName) > 80 {
		writeError(w, http.StatusBadRequest, "display_name is too long")
		return
	}
	hash, err := hashPassword(in.Password)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not create account")
		return
	}
	id := uuid.New()
	_, err = a.db.Exec(r.Context(), `
		INSERT INTO users (id, username, display_name, password_hash)
		VALUES ($1, $2, $3, $4)
	`, id, in.Username, in.DisplayName, hash)
	if err != nil {
		if strings.Contains(strings.ToLower(err.Error()), "unique") || strings.Contains(err.Error(), "23505") {
			writeError(w, http.StatusConflict, "username already exists")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not create account")
		return
	}
	token, err := a.issueToken(id, in.Username)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not create session")
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{
		"token": token,
		"user": map[string]any{"id": id, "username": in.Username, "display_name": in.DisplayName},
	})
}

func (a *App) login(w http.ResponseWriter, r *http.Request) {
	var in struct {
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if !decodeJSON(w, r, &in) {
		return
	}
	in.Username = strings.ToLower(strings.TrimSpace(in.Username))
	var id uuid.UUID
	var hash, displayName string
	err := a.db.QueryRow(r.Context(), `
		SELECT id, password_hash, display_name FROM users WHERE username=$1
	`, in.Username).Scan(&id, &hash, &displayName)
	if err != nil || !verifyPassword(in.Password, hash) {
		writeError(w, http.StatusUnauthorized, "invalid username or password")
		return
	}
	token, err := a.issueToken(id, in.Username)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not create session")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"token": token,
		"user": map[string]any{"id": id, "username": in.Username, "display_name": displayName},
	})
}

func (a *App) me(w http.ResponseWriter, r *http.Request) {
	c := currentUser(r)
	var displayName string
	var created time.Time
	if err := a.db.QueryRow(r.Context(), `SELECT display_name, created_at FROM users WHERE id=$1`, c.UserID).Scan(&displayName, &created); err != nil {
		writeError(w, http.StatusNotFound, "account not found")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"id": c.UserID, "username": c.Username, "display_name": displayName, "created_at": created,
	})
}

func (a *App) createConversation(w http.ResponseWriter, r *http.Request) {
	c := currentUser(r)
	var in struct {
		Kind    string   `json:"kind"`
		Title   string   `json:"title"`
		Members []string `json:"members"`
	}
	if !decodeJSON(w, r, &in) {
		return
	}
	in.Kind = strings.ToLower(strings.TrimSpace(in.Kind))
	if in.Kind != "private" && in.Kind != "group" && in.Kind != "channel" {
		writeError(w, http.StatusBadRequest, "kind must be private, group, or channel")
		return
	}
	if in.Kind == "private" && len(in.Members) != 1 {
		writeError(w, http.StatusBadRequest, "private conversations require exactly one other member")
		return
	}
	if in.Kind != "private" && strings.TrimSpace(in.Title) == "" {
		writeError(w, http.StatusBadRequest, "groups and channels require a title")
		return
	}

	tx, err := a.db.Begin(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}
	defer tx.Rollback(r.Context())

	memberIDs := []uuid.UUID{uuid.MustParse(c.UserID)}
	seen := map[string]bool{c.Username: true}
	for _, raw := range in.Members {
		username := strings.ToLower(strings.TrimSpace(raw))
		if username == "" || seen[username] {
			continue
		}
		seen[username] = true
		var uid uuid.UUID
		if err := tx.QueryRow(r.Context(), `SELECT id FROM users WHERE username=$1`, username).Scan(&uid); err != nil {
			writeError(w, http.StatusBadRequest, "unknown member: "+username)
			return
		}
		memberIDs = append(memberIDs, uid)
	}

	conversationID := uuid.New()
	_, err = tx.Exec(r.Context(), `
		INSERT INTO conversations (id, kind, title, owner_id) VALUES ($1, $2, $3, $4)
	`, conversationID, in.Kind, strings.TrimSpace(in.Title), c.UserID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not create conversation")
		return
	}
	for _, uid := range memberIDs {
		role := "member"
		if uid.String() == c.UserID {
			role = "owner"
		}
		if _, err := tx.Exec(r.Context(), `
			INSERT INTO conversation_members (conversation_id, user_id, role) VALUES ($1, $2, $3)
		`, conversationID, uid, role); err != nil {
			writeError(w, http.StatusInternalServerError, "could not add members")
			return
		}
	}
	if err := tx.Commit(r.Context()); err != nil {
		writeError(w, http.StatusInternalServerError, "could not save conversation")
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"id": conversationID, "kind": in.Kind, "title": in.Title})
}

func (a *App) listConversations(w http.ResponseWriter, r *http.Request) {
	c := currentUser(r)
	rows, err := a.db.Query(r.Context(), `
		SELECT c.id, c.kind, c.title, c.owner_id, c.created_at,
		       COALESCE((SELECT MAX(m.sequence) FROM messages m WHERE m.conversation_id=c.id), 0)
		FROM conversations c
		JOIN conversation_members cm ON cm.conversation_id=c.id
		WHERE cm.user_id=$1
		ORDER BY c.created_at DESC
	`, c.UserID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load conversations")
		return
	}
	defer rows.Close()
	items := make([]map[string]any, 0)
	for rows.Next() {
		var id, owner uuid.UUID
		var kind, title string
		var created time.Time
		var sequence int64
		if err := rows.Scan(&id, &kind, &title, &owner, &created, &sequence); err != nil {
			continue
		}
		items = append(items, map[string]any{
			"id": id, "kind": kind, "title": title, "owner_id": owner, "created_at": created, "last_sequence": sequence,
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{"conversations": items})
}

func (a *App) sendMessage(w http.ResponseWriter, r *http.Request) {
	c := currentUser(r)
	conversationID, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid conversation id")
		return
	}
	var in struct {
		ClientID   string          `json:"client_id"`
		Ciphertext string          `json:"ciphertext"`
		Envelope   json.RawMessage `json:"envelope"`
	}
	if !decodeJSON(w, r, &in) {
		return
	}
	if len(in.Ciphertext) == 0 || len(in.Ciphertext) > 2_000_000 {
		writeError(w, http.StatusBadRequest, "ciphertext is required and must be at most 2 MB")
		return
	}
	if len(in.Envelope) == 0 || !json.Valid(in.Envelope) {
		writeError(w, http.StatusBadRequest, "envelope must be valid JSON")
		return
	}
	if in.ClientID == "" {
		in.ClientID = uuid.NewString()
	}

	var kind, role string
	err = a.db.QueryRow(r.Context(), `
		SELECT c.kind, cm.role
		FROM conversations c
		JOIN conversation_members cm ON cm.conversation_id=c.id
		WHERE c.id=$1 AND cm.user_id=$2
	`, conversationID, c.UserID).Scan(&kind, &role)
	if err != nil {
		writeError(w, http.StatusForbidden, "not a member of this conversation")
		return
	}
	if kind == "channel" && role != "owner" && role != "admin" {
		writeError(w, http.StatusForbidden, "only channel admins can post")
		return
	}

	messageID := uuid.New()
	var sequence int64
	var created time.Time
	err = a.db.QueryRow(r.Context(), `
		INSERT INTO messages (id, conversation_id, sender_id, client_id, ciphertext, envelope)
		VALUES ($1, $2, $3, $4, $5, $6)
		ON CONFLICT (conversation_id, sender_id, client_id)
		DO UPDATE SET client_id=EXCLUDED.client_id
		RETURNING sequence, created_at
	`, messageID, conversationID, c.UserID, in.ClientID, in.Ciphertext, in.Envelope).Scan(&sequence, &created)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not store message")
		return
	}

	memberRows, err := a.db.Query(r.Context(), `SELECT user_id FROM conversation_members WHERE conversation_id=$1`, conversationID)
	if err == nil {
		defer memberRows.Close()
		members := make([]string, 0)
		for memberRows.Next() {
			var uid uuid.UUID
			if memberRows.Scan(&uid) == nil {
				members = append(members, uid.String())
			}
		}
		a.hub.broadcast(members, map[string]any{
			"type": "message.new",
			"message": map[string]any{
				"id": messageID, "conversation_id": conversationID, "sender_id": c.UserID,
				"client_id": in.ClientID, "ciphertext": in.Ciphertext, "envelope": json.RawMessage(in.Envelope),
				"sequence": sequence, "created_at": created,
			},
		})
	}
	writeJSON(w, http.StatusCreated, map[string]any{"id": messageID, "sequence": sequence, "created_at": created})
}

func (a *App) listMessages(w http.ResponseWriter, r *http.Request) {
	c := currentUser(r)
	conversationID, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid conversation id")
		return
	}
	var exists bool
	if err := a.db.QueryRow(r.Context(), `
		SELECT EXISTS(SELECT 1 FROM conversation_members WHERE conversation_id=$1 AND user_id=$2)
	`, conversationID, c.UserID).Scan(&exists); err != nil || !exists {
		writeError(w, http.StatusForbidden, "not a member of this conversation")
		return
	}
	after, _ := strconv.ParseInt(r.URL.Query().Get("after"), 10, 64)
	rows, err := a.db.Query(r.Context(), `
		SELECT id, sender_id, client_id, ciphertext, envelope, sequence, created_at
		FROM messages
		WHERE conversation_id=$1 AND sequence>$2
		ORDER BY sequence ASC
		LIMIT 200
	`, conversationID, after)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load messages")
		return
	}
	defer rows.Close()
	items := make([]map[string]any, 0)
	for rows.Next() {
		var id, sender uuid.UUID
		var clientID, ciphertext string
		var envelope []byte
		var sequence int64
		var created time.Time
		if rows.Scan(&id, &sender, &clientID, &ciphertext, &envelope, &sequence, &created) != nil {
			continue
		}
		items = append(items, map[string]any{
			"id": id, "conversation_id": conversationID, "sender_id": sender, "client_id": clientID,
			"ciphertext": ciphertext, "envelope": json.RawMessage(envelope), "sequence": sequence, "created_at": created,
		})
	}
	writeJSON(w, http.StatusOK, map[string]any{"messages": items})
}

func (a *App) putKeyBundle(w http.ResponseWriter, r *http.Request) {
	c := currentUser(r)
	var in struct {
		IdentityKey       string   `json:"identity_key"`
		SignedPreKey      string   `json:"signed_prekey"`
		SignedPreKeySig   string   `json:"signed_prekey_signature"`
		OneTimePreKeys    []string `json:"one_time_prekeys"`
	}
	if !decodeJSON(w, r, &in) {
		return
	}
	if in.IdentityKey == "" || in.SignedPreKey == "" || in.SignedPreKeySig == "" {
		writeError(w, http.StatusBadRequest, "identity and signed pre-key material is required")
		return
	}
	if len(in.OneTimePreKeys) > 500 {
		writeError(w, http.StatusBadRequest, "too many one-time prekeys")
		return
	}
	tx, err := a.db.Begin(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}
	defer tx.Rollback(r.Context())
	_, err = tx.Exec(r.Context(), `
		INSERT INTO key_bundles (user_id, identity_key, signed_prekey, signed_prekey_signature, updated_at)
		VALUES ($1, $2, $3, $4, now())
		ON CONFLICT (user_id) DO UPDATE SET
		identity_key=EXCLUDED.identity_key,
		signed_prekey=EXCLUDED.signed_prekey,
		signed_prekey_signature=EXCLUDED.signed_prekey_signature,
		updated_at=now()
	`, c.UserID, in.IdentityKey, in.SignedPreKey, in.SignedPreKeySig)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not save key bundle")
		return
	}
	if _, err := tx.Exec(r.Context(), `DELETE FROM one_time_prekeys WHERE user_id=$1`, c.UserID); err != nil {
		writeError(w, http.StatusInternalServerError, "could not replace prekeys")
		return
	}
	for _, key := range in.OneTimePreKeys {
		if strings.TrimSpace(key) == "" {
			continue
		}
		if _, err := tx.Exec(r.Context(), `INSERT INTO one_time_prekeys (user_id, prekey) VALUES ($1, $2)`, c.UserID, key); err != nil {
			writeError(w, http.StatusInternalServerError, "could not save prekeys")
			return
		}
	}
	if err := tx.Commit(r.Context()); err != nil {
		writeError(w, http.StatusInternalServerError, "could not save key bundle")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (a *App) getKeyBundle(w http.ResponseWriter, r *http.Request) {
	username := strings.ToLower(strings.TrimSpace(r.PathValue("username")))
	tx, err := a.db.BeginTx(r.Context(), pgx.TxOptions{})
	if err != nil {
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}
	defer tx.Rollback(r.Context())
	var userID uuid.UUID
	var identityKey, signedPreKey, signature string
	err = tx.QueryRow(r.Context(), `
		SELECT u.id, k.identity_key, k.signed_prekey, k.signed_prekey_signature
		FROM users u JOIN key_bundles k ON k.user_id=u.id
		WHERE u.username=$1
	`, username).Scan(&userID, &identityKey, &signedPreKey, &signature)
	if err != nil {
		writeError(w, http.StatusNotFound, "key bundle not found")
		return
	}
	var oneTime *string
	var key string
	err = tx.QueryRow(r.Context(), `
		DELETE FROM one_time_prekeys
		WHERE id=(SELECT id FROM one_time_prekeys WHERE user_id=$1 ORDER BY id ASC LIMIT 1 FOR UPDATE SKIP LOCKED)
		RETURNING prekey
	`, userID).Scan(&key)
	if err == nil {
		oneTime = &key
	} else if !errors.Is(err, pgx.ErrNoRows) {
		writeError(w, http.StatusInternalServerError, "could not reserve prekey")
		return
	}
	if err := tx.Commit(r.Context()); err != nil {
		writeError(w, http.StatusInternalServerError, "could not load key bundle")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"username": username,
		"identity_key": identityKey,
		"signed_prekey": signedPreKey,
		"signed_prekey_signature": signature,
		"one_time_prekey": oneTime,
	})
}

func (a *App) websocket(w http.ResponseWriter, r *http.Request) {
	c := currentUser(r)
	conn, err := a.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	client := a.hub.add(c.UserID, conn)
	defer a.hub.remove(c.UserID, client)
	conn.SetReadLimit(64 * 1024)
	_ = conn.SetReadDeadline(time.Now().Add(90 * time.Second))
	conn.SetPongHandler(func(string) error {
		return conn.SetReadDeadline(time.Now().Add(90 * time.Second))
	})
	for {
		if _, _, err := conn.ReadMessage(); err != nil {
			return
		}
	}
}

func (a *App) auth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		header := strings.TrimSpace(r.Header.Get("Authorization"))
		if !strings.HasPrefix(header, "Bearer ") {
			writeError(w, http.StatusUnauthorized, "missing bearer token")
			return
		}
		tokenString := strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
		parsed := &claims{}
		token, err := jwt.ParseWithClaims(tokenString, parsed, func(token *jwt.Token) (any, error) {
			if token.Method != jwt.SigningMethodHS256 {
				return nil, errors.New("unexpected signing method")
			}
			return a.jwtSecret, nil
		}, jwt.WithIssuer("lattice-server"), jwt.WithExpirationRequired())
		if err != nil || !token.Valid {
			writeError(w, http.StatusUnauthorized, "invalid session")
			return
		}
		if _, err := uuid.Parse(parsed.UserID); err != nil {
			writeError(w, http.StatusUnauthorized, "invalid session")
			return
		}
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), userContextKey, parsed)))
	})
}

func (a *App) issueToken(id uuid.UUID, username string) (string, error) {
	now := time.Now()
	c := claims{
		UserID: id.String(), Username: username,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer: "lattice-server", Subject: id.String(), IssuedAt: jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(30 * 24 * time.Hour)),
		},
	}
	return jwt.NewWithClaims(jwt.SigningMethodHS256, c).SignedString(a.jwtSecret)
}

func currentUser(r *http.Request) *claims {
	return r.Context().Value(userContextKey).(*claims)
}

type wsClient struct {
	conn *websocket.Conn
	mu   sync.Mutex
}

type hub struct {
	mu      sync.RWMutex
	clients map[string]map[*wsClient]struct{}
}

func newHub() *hub { return &hub{clients: make(map[string]map[*wsClient]struct{})} }

func (h *hub) add(userID string, conn *websocket.Conn) *wsClient {
	client := &wsClient{conn: conn}
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.clients[userID] == nil {
		h.clients[userID] = make(map[*wsClient]struct{})
	}
	h.clients[userID][client] = struct{}{}
	return client
}

func (h *hub) remove(userID string, client *wsClient) {
	h.mu.Lock()
	if clients := h.clients[userID]; clients != nil {
		delete(clients, client)
		if len(clients) == 0 {
			delete(h.clients, userID)
		}
	}
	h.mu.Unlock()
	client.conn.Close()
}

func (h *hub) broadcast(userIDs []string, payload any) {
	h.mu.RLock()
	clients := make([]*wsClient, 0)
	seen := make(map[*wsClient]bool)
	for _, userID := range userIDs {
		for client := range h.clients[userID] {
			if !seen[client] {
				seen[client] = true
				clients = append(clients, client)
			}
	}
	h.mu.RUnlock()
	for _, client := range clients {
		client.mu.Lock()
		_ = client.conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
		err := client.conn.WriteJSON(payload)
		client.mu.Unlock()
		if err != nil {
			_ = client.conn.Close()
		}
	}
}

func hashPassword(password string) (string, error) {
	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		return "", err
	}
	const memory = 64 * 1024
	const iterations = 3
	const parallelism = 2
	const keyLen = 32
	hash := argon2.IDKey([]byte(password), salt, iterations, memory, parallelism, keyLen)
	return fmt.Sprintf("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s", memory, iterations, parallelism,
		base64.RawStdEncoding.EncodeToString(salt), base64.RawStdEncoding.EncodeToString(hash)), nil
}

func verifyPassword(password, encoded string) bool {
	var memory uint32
	var iterations uint32
	var parallelism uint8
	var saltB64, hashB64 string
	if _, err := fmt.Sscanf(encoded, "$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s", &memory, &iterations, &parallelism, &saltB64, &hashB64); err != nil {
		return false
	}
	// fmt.Sscanf with %s does not stop at '$', so parse explicitly.
	parts := strings.Split(encoded, "$")
	if len(parts) != 6 {
		return false
	}
	if _, err := fmt.Sscanf(parts[3], "m=%d,t=%d,p=%d", &memory, &iterations, &parallelism); err != nil {
		return false
	}
	salt, err := base64.RawStdEncoding.DecodeString(parts[4])
	if err != nil {
		return false
	}
	expected, err := base64.RawStdEncoding.DecodeString(parts[5])
	if err != nil || len(expected) == 0 {
		return false
	}
	actual := argon2.IDKey([]byte(password), salt, iterations, memory, parallelism, uint32(len(expected)))
	return subtle.ConstantTimeCompare(actual, expected) == 1
}

func decodeJSON(w http.ResponseWriter, r *http.Request, out any) bool {
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, 3<<20))
	dec.DisallowUnknownFields()
	if err := dec.Decode(out); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON")
		return false
	}
	return true
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]any{"error": message})
}

func requestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		log.Printf("%s %s %s", r.Method, r.URL.Path, time.Since(start).Round(time.Millisecond))
	})
}
