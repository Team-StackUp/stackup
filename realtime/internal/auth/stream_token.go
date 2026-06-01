package auth

import (
	"crypto/sha256"
	"errors"
	"fmt"

	"github.com/golang-jwt/jwt/v5"
)

const (
	streamTokenType = "STREAM"
	sseConnectScope = "SSE_CONNECT"
)

var ErrInvalidStreamToken = errors.New("invalid stream token")

// Claims holds the verified identity and resource scope from a stream token.
type Claims struct {
	UserID       int64
	ResourceType string
	ResourceID   int64
}

// StreamTokenVerifier validates Core-issued SSE stream tokens.
// It mirrors Core StreamTokenProvider: HS256 over key = SHA-256(jwtSecret).
type StreamTokenVerifier struct {
	key []byte
}

func NewStreamTokenVerifier(jwtSecret string) *StreamTokenVerifier {
	sum := sha256.Sum256([]byte(jwtSecret))
	return &StreamTokenVerifier{key: sum[:]}
}

// Verify checks signature, expiry, tokenType, scope and returns the Claims.
func (v *StreamTokenVerifier) Verify(token string) (Claims, error) {
	claims := jwt.MapClaims{}
	parsed, err := jwt.ParseWithClaims(token, claims, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("%w: unexpected signing method", ErrInvalidStreamToken)
		}
		return v.key, nil
	})
	if err != nil || !parsed.Valid {
		return Claims{}, ErrInvalidStreamToken
	}
	if s, _ := claims["tokenType"].(string); s != streamTokenType {
		return Claims{}, ErrInvalidStreamToken
	}
	if s, _ := claims["scope"].(string); s != sseConnectScope {
		return Claims{}, ErrInvalidStreamToken
	}
	uid, ok := claims["userId"].(float64)
	if !ok {
		return Claims{}, ErrInvalidStreamToken
	}
	out := Claims{UserID: int64(uid)}
	if rt, ok := claims["resourceType"].(string); ok {
		out.ResourceType = rt
	}
	if rid, ok := claims["resourceId"].(float64); ok {
		out.ResourceID = int64(rid)
	}
	return out, nil
}
