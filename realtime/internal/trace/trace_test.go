package trace

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestMiddlewareUsesIncomingHeader(t *testing.T) {
	var captured string
	h := Middleware(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		captured = FromContext(r.Context())
	}))

	req := httptest.NewRequest("GET", "/x", nil)
	req.Header.Set("X-Trace-Id", "incoming-trace")
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if captured != "incoming-trace" {
		t.Errorf("captured = %q", captured)
	}
	if got := rec.Header().Get("X-Trace-Id"); got != "incoming-trace" {
		t.Errorf("response header = %q", got)
	}
}

func TestMiddlewareGeneratesWhenMissing(t *testing.T) {
	var captured string
	h := Middleware(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		captured = FromContext(r.Context())
	}))

	req := httptest.NewRequest("GET", "/x", nil)
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if captured == "" {
		t.Error("expected generated trace id, got empty")
	}
	if got := rec.Header().Get("X-Trace-Id"); got != captured {
		t.Errorf("response header = %q, want %q", got, captured)
	}
}

func TestFromContextEmptyWithoutInjection(t *testing.T) {
	if got := FromContext(context.Background()); got != "" {
		t.Errorf("FromContext = %q, want empty", got)
	}
}
