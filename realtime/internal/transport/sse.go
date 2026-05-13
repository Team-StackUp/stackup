package transport

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
	"github.com/Team-StackUp/stackup/realtime/internal/trace"
	"github.com/go-chi/chi/v5"
)

type SSEHandler struct {
	Registry        *session.Registry
	BufferSize      int
	PingInterval    time.Duration
	HeartbeatPrefix string
}

func NewSSEHandler(r *session.Registry, bufferSize int, pingInterval time.Duration) *SSEHandler {
	return &SSEHandler{
		Registry:        r,
		BufferSize:      bufferSize,
		PingInterval:    pingInterval,
		HeartbeatPrefix: ": ping ",
	}
}

func (h *SSEHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	sid, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil || sid <= 0 {
		http.Error(w, "invalid session id", http.StatusBadRequest)
		return
	}

	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming unsupported", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache, no-transform")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
	flusher.Flush()

	traceID := trace.FromContext(r.Context())
	slog.Info("sse.subscribe", "session_id", sid, "trace_id", traceID)

	sub := h.Registry.Subscribe(sid, h.BufferSize)
	defer h.Registry.Unsubscribe(sid, sub)

	ctx := r.Context()
	ticker := time.NewTicker(h.PingInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			slog.Info("sse.unsubscribe", "session_id", sid, "reason", "client_close")
			return
		case <-ticker.C:
			if _, err := fmt.Fprintf(w, "%s%d\n\n", h.HeartbeatPrefix, time.Now().Unix()); err != nil {
				return
			}
			flusher.Flush()
		case ev, ok := <-sub.Ch:
			if !ok {
				return
			}
			if _, err := writeSSE(w, ev); err != nil {
				return
			}
			flusher.Flush()
		}
	}
}

func writeSSE(w http.ResponseWriter, ev session.Event) (int, error) {
	return fmt.Fprintf(w, "id: %s\nevent: %s\ndata: %s\n\n", ev.ID, ev.Type, ev.Data)
}

// for tests
var _ = context.Background
