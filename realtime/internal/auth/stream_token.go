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

// StreamTokenVerifier validates Core-issued SSE stream tokens.
// It mirrors Core StreamTokenProvider: HS256 over key = SHA-256(jwtSecret).
type StreamTokenVerifier struct {
	key []byte
}

func NewStreamTokenVerifier(jwtSecret string) *StreamTokenVerifier {
	sum := sha256.Sum256([]byte(jwtSecret))
	return &StreamTokenVerifier{key: sum[:]}
}

// Verify checks signature, expiry, tokenType, scope and returns the userId.
func (v *StreamTokenVerifier) Verify(token string) (int64, error) {
	claims := jwt.MapClaims{}
	parsed, err := jwt.ParseWithClaims(token, claims, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("%w: unexpected signing method", ErrInvalidStreamToken)
		}
		return v.key, nil
	})
	if err != nil || !parsed.Valid {
		return 0, ErrInvalidStreamToken
	}
	if s, _ := claims["tokenType"].(string); s != streamTokenType {
		return 0, ErrInvalidStreamToken
	}
	if s, _ := claims["scope"].(string); s != sseConnectScope {
		return 0, ErrInvalidStreamToken
	}
	raw, ok := claims["userId"]
	if !ok {
		return 0, ErrInvalidStreamToken
	}
	// JSON numbers decode to float64 in MapClaims.
	f, ok := raw.(float64)
	if !ok {
		return 0, ErrInvalidStreamToken
	}
	return int64(f), nil
}
