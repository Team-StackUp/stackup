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
	// WriteTimeout bounds a single response write. Without it a stalled client
	// (laptop sleep, dead network) blocks this goroutine until the OS gives up,
	// which can take minutes — see ServeChannel.
	WriteTimeout time.Duration
}

func NewSSEHandler(
	r *session.Registry,
	bufferSize int,
	pingInterval time.Duration,
	writeTimeout time.Duration,
) *SSEHandler {
	if writeTimeout <= 0 {
		writeTimeout = 10 * time.Second
	}
	return &SSEHandler{
		Registry:        r,
		BufferSize:      bufferSize,
		PingInterval:    pingInterval,
		HeartbeatPrefix: ": ping ",
		WriteTimeout:    writeTimeout,
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

	// Every write gets a deadline.
	//
	// The WS path already had one (WSWriteTimeout); SSE had none. A stalled
	// client — laptop lid closed mid-interview, network drop — leaves the TCP
	// connection open while its receive window stays full, so Fprintf blocks
	// here indefinitely. This goroutine then stops draining sub.Ch, the buffer
	// fills, and every Dispatch to this channel pays the slow-consumer timeout.
	// The AMQP consumer is single-threaded, so that stall delays *other*
	// sessions' events too. Bounding the write lets us give up and unsubscribe.
	rc := http.NewResponseController(w)
	deadline := func() bool {
		return rc.SetWriteDeadline(time.Now().Add(h.WriteTimeout)) == nil
	}
	// A server without deadline support (test recorders, some middlewares)
	// simply keeps the old behaviour rather than failing the stream.
	supportsDeadline := deadline()

	ctx := r.Context()
	ticker := time.NewTicker(h.PingInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			slog.Info("sse.unsubscribe", "channel_kind", channel.Kind, "channel_id", channel.ID, "reason", "client_close")
			return
		case <-ticker.C:
			if supportsDeadline {
				deadline()
			}
			if _, err := fmt.Fprintf(w, "%s%d\n\n", h.HeartbeatPrefix, time.Now().Unix()); err != nil {
				slog.Info("sse.unsubscribe", "channel_kind", channel.Kind,
					"channel_id", channel.ID, "reason", "ping_write_failed", "err", err)
				return
			}
			flusher.Flush()
		case ev, ok := <-sub.Ch:
			if !ok {
				return
			}
			if supportsDeadline {
				deadline()
			}
			if _, err := writeSSE(w, ev); err != nil {
				slog.Info("sse.unsubscribe", "channel_kind", channel.Kind,
					"channel_id", channel.ID, "reason", "write_failed", "err", err)
				return
			}
			flusher.Flush()
		}
	}
}

func writeSSE(w http.ResponseWriter, ev session.Event) (int, error) {
	return fmt.Fprintf(w, "id: %s\nevent: %s\ndata: %s\n\n", ev.ID, ev.Type, ev.Data)
}
