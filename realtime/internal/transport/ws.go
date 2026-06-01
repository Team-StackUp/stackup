package transport

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/auth"
	"github.com/Team-StackUp/stackup/realtime/internal/core"
	"github.com/Team-StackUp/stackup/realtime/internal/session"
	"github.com/Team-StackUp/stackup/realtime/internal/trace"
	"github.com/coder/websocket"
	"github.com/go-chi/chi/v5"
)

type WSHandler struct {
	Registry     *session.Registry
	Core         core.AnswerSubmitter
	BufferSize   int
	WriteTimeout time.Duration
}

func NewWSHandler(reg *session.Registry, c core.AnswerSubmitter, writeTimeout time.Duration) *WSHandler {
	return &WSHandler{Registry: reg, Core: c, BufferSize: 16, WriteTimeout: writeTimeout}
}

type inboundMessage struct {
	Type           string `json:"type"`
	Content        string `json:"content"`
	IdempotencyKey string `json:"idempotencyKey"`
}

type outboundFrame struct {
	ID    string          `json:"id"`
	Event string          `json:"event"`
	Data  json.RawMessage `json:"data"`
}

func (h *WSHandler) ServeWS(w http.ResponseWriter, r *http.Request) {
	sid, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil || sid <= 0 {
		http.Error(w, "invalid session id", http.StatusBadRequest)
		return
	}
	userID := auth.UserIDFromContext(r.Context())
	traceID := trace.FromContext(r.Context())

	conn, err := websocket.Accept(w, r, nil)
	if err != nil {
		slog.Warn("ws.accept.failed", "err", err)
		return
	}
	defer conn.CloseNow()

	ctx := r.Context()
	channel := session.Channel{Kind: session.ChannelSession, ID: sid}
	sub := h.Registry.Subscribe(channel, h.BufferSize)
	defer h.Registry.Unsubscribe(channel, sub)
	slog.Info("ws.subscribe", "session_id", sid, "user_id", userID, "trace_id", traceID)

	go h.writeLoop(ctx, conn, sub)

	for {
		_, data, err := conn.Read(ctx)
		if err != nil {
			slog.Info("ws.read.closed", "session_id", sid, "err", err)
			return
		}
		var msg inboundMessage
		if err := json.Unmarshal(data, &msg); err != nil || msg.Type != "answer" {
			h.writeError(ctx, conn, "invalid message")
			continue
		}
		if err := h.Core.SubmitAnswer(ctx, sid, userID, msg.Content, msg.IdempotencyKey); err != nil {
			slog.Warn("ws.answer.submit.failed", "session_id", sid, "err", err)
			h.writeError(ctx, conn, "answer submit failed")
		}
	}
}

func (h *WSHandler) writeLoop(ctx context.Context, conn *websocket.Conn, sub *session.Subscriber) {
	for {
		select {
		case <-ctx.Done():
			return
		case ev, ok := <-sub.Ch:
			if !ok {
				return
			}
			frame, _ := json.Marshal(outboundFrame{ID: ev.ID, Event: ev.Type, Data: ev.Data})
			writeCtx, cancel := context.WithTimeout(ctx, h.WriteTimeout)
			err := conn.Write(writeCtx, websocket.MessageText, frame)
			cancel()
			if err != nil {
				return
			}
		}
	}
}

func (h *WSHandler) writeError(ctx context.Context, conn *websocket.Conn, message string) {
	frame, _ := json.Marshal(outboundFrame{Event: "error", Data: json.RawMessage(`"` + message + `"`)})
	writeCtx, cancel := context.WithTimeout(ctx, h.WriteTimeout)
	defer cancel()
	_ = conn.Write(writeCtx, websocket.MessageText, frame)
}
