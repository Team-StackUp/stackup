package core

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestSubmitAnswerPostsToCore(t *testing.T) {
	var gotPath, gotKey, gotBody string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotKey = r.Header.Get("X-Internal-API-Key")
		b, _ := io.ReadAll(r.Body)
		gotBody = string(b)
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":501}`))
	}))
	defer srv.Close()

	c := NewClient(srv.URL, "secret-key", 2*time.Second)
	err := c.SubmitAnswer(context.Background(), 99, 7, "내 답변", "idem-1")
	if err != nil {
		t.Fatalf("SubmitAnswer err: %v", err)
	}
	if gotPath != "/api/internal/sessions/99/messages" {
		t.Errorf("path = %s", gotPath)
	}
	if gotKey != "secret-key" {
		t.Errorf("api key header = %q", gotKey)
	}
	var body map[string]any
	if err := json.Unmarshal([]byte(gotBody), &body); err != nil {
		t.Fatalf("body not json: %v", err)
	}
	if body["userId"] != float64(7) || body["content"] != "내 답변" || body["idempotencyKey"] != "idem-1" {
		t.Errorf("body = %s", gotBody)
	}
}

func TestSubmitAnswerNon2xxReturnsError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnprocessableEntity)
	}))
	defer srv.Close()

	c := NewClient(srv.URL, "k", 2*time.Second)
	if err := c.SubmitAnswer(context.Background(), 1, 1, "x", ""); err == nil {
		t.Error("expected error on 422")
	}
}
