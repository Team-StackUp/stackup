package auth

import (
	"crypto/sha256"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const testSecret = "test-jwt-secret"

// mintToken builds a Core-equivalent stream token for tests.
func mintToken(t *testing.T, claims jwt.MapClaims) string {
	t.Helper()
	key := sha256.Sum256([]byte(testSecret))
	tok := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	s, err := tok.SignedString(key[:])
	if err != nil {
		t.Fatalf("sign: %v", err)
	}
	return s
}

func validClaims() jwt.MapClaims {
	return jwt.MapClaims{
		"sub":       "42",
		"userId":    42,
		"tokenType": "STREAM",
		"scope":     "SSE_CONNECT",
		"exp":       time.Now().Add(time.Minute).Unix(),
	}
}

func TestVerifyValidToken(t *testing.T) {
	v := NewStreamTokenVerifier(testSecret)
	userID, err := v.Verify(mintToken(t, validClaims()))
	if err != nil {
		t.Fatalf("Verify err: %v", err)
	}
	if userID != 42 {
		t.Errorf("userID = %d, want 42", userID)
	}
}

func TestVerifyRejectsExpired(t *testing.T) {
	v := NewStreamTokenVerifier(testSecret)
	c := validClaims()
	c["exp"] = time.Now().Add(-time.Minute).Unix()
	if _, err := v.Verify(mintToken(t, c)); err == nil {
		t.Error("expected error for expired token")
	}
}

func TestVerifyRejectsWrongTokenType(t *testing.T) {
	v := NewStreamTokenVerifier(testSecret)
	c := validClaims()
	c["tokenType"] = "ACCESS"
	if _, err := v.Verify(mintToken(t, c)); err == nil {
		t.Error("expected error for wrong tokenType")
	}
}

func TestVerifyRejectsWrongScope(t *testing.T) {
	v := NewStreamTokenVerifier(testSecret)
	c := validClaims()
	c["scope"] = "OTHER"
	if _, err := v.Verify(mintToken(t, c)); err == nil {
		t.Error("expected error for wrong scope")
	}
}

func TestVerifyRejectsBadSignature(t *testing.T) {
	v := NewStreamTokenVerifier("different-secret")
	if _, err := v.Verify(mintToken(t, validClaims())); err == nil {
		t.Error("expected error for bad signature")
	}
}
