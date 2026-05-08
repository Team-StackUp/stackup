package transport

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
)

func TestSSEHandlerRejectsNonNumericID(t *testing.T) {
	reg := session.NewRegistry()
	sse := NewSSEHandler(reg, 4, 1*time.Second)
	r := NewRouter(reg, sse)

	req := httptest.NewRequest("GET", "/realtime/sessions/abc", nil)
	rec := httptest.NewRecorder()

	r.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want 400", rec.Code)
	}
}

func TestSSEHandlerRejectsZeroID(t *testing.T) {
	reg := session.NewRegistry()
	sse := NewSSEHandler(reg, 4, 1*time.Second)
	r := NewRouter(reg, sse)

	req := httptest.NewRequest("GET", "/realtime/sessions/0", nil)
	rec := httptest.NewRecorder()

	r.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want 400", rec.Code)
	}
}

func TestHealthEndpoint(t *testing.T) {
	reg := session.NewRegistry()
	sse := NewSSEHandler(reg, 4, 1*time.Second)
	r := NewRouter(reg, sse)

	req := httptest.NewRequest("GET", "/health", nil)
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("status = %d", rec.Code)
	}
	if rec.Body.String() != `{"status":"ok"}` {
		t.Errorf("body = %q", rec.Body.String())
	}
}
