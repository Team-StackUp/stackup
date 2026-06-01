package auth

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestMiddlewareRejectsMissingToken(t *testing.T) {
	mw := Middleware(NewStreamTokenVerifier(testSecret))
	h := mw(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("handler should not be reached")
	}))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/realtime/stream/me", nil))
	if rec.Code != http.StatusUnauthorized {
		t.Errorf("code = %d, want 401", rec.Code)
	}
}

func TestMiddlewarePassesValidTokenAndInjectsUserID(t *testing.T) {
	mw := Middleware(NewStreamTokenVerifier(testSecret))
	var gotUserID int64
	h := mw(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotUserID = UserIDFromContext(r.Context())
		w.WriteHeader(http.StatusOK)
	}))
	token := mintToken(t, validClaims())
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/realtime/stream/me?access_token="+token, nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("code = %d, want 200", rec.Code)
	}
	if gotUserID != 42 {
		t.Errorf("userID = %d, want 42", gotUserID)
	}
}
