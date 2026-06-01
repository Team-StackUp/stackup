package transport

import (
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
	"github.com/Team-StackUp/stackup/realtime/internal/trace"
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

// ServeChannel streams events for the given channel to the client until the
// request context is cancelled.
func (h *SSEHandler) ServeChannel(w http.ResponseWriter, r *http.Request, channel session.Channel) {
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
	slog.Info("sse.subscribe", "channel_kind", channel.Kind, "channel_id", channel.ID, "trace_id", traceID)

	sub := h.Registry.Subscribe(channel, h.BufferSize)
	defer h.Registry.Unsubscribe(channel, sub)

	ctx := r.Context()
	ticker := time.NewTicker(h.PingInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			slog.Info("sse.unsubscribe", "channel_kind", channel.Kind, "channel_id", channel.ID, "reason", "client_close")
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
