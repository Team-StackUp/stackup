package transport

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/auth"
	"github.com/Team-StackUp/stackup/realtime/internal/trace"
	"github.com/coder/websocket"
	"github.com/go-chi/chi/v5"
)

// WSAudioHandler 는 브라우저 WS(오디오 업/자막 다운)를 AI WS 로 양방향 프록시한다.
// RealTime 은 오디오 내용을 해석하지 않는다 (순수 바이트 파이프 + 종료 처리).
type WSAudioHandler struct {
	AIBaseURL    string
	InternalKey  string
	WriteTimeout time.Duration
}

func NewWSAudioHandler(aiWSURL, internalKey string, writeTimeout time.Duration) *WSAudioHandler {
	return &WSAudioHandler{AIBaseURL: aiWSURL, InternalKey: internalKey, WriteTimeout: writeTimeout}
}

func (h *WSAudioHandler) ServeAudioWS(w http.ResponseWriter, r *http.Request) {
	sid, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil || sid <= 0 {
		http.Error(w, "invalid session id", http.StatusBadRequest)
		return
	}
	mid, err := strconv.ParseInt(r.URL.Query().Get("messageId"), 10, 64)
	if err != nil || mid <= 0 {
		http.Error(w, "invalid messageId", http.StatusBadRequest)
		return
	}
	userID := auth.UserIDFromContext(r.Context())
	traceID := trace.FromContext(r.Context())

	client, err := websocket.Accept(w, r, nil)
	if err != nil {
		slog.Warn("ws_audio.accept.failed", "err", err)
		return
	}
	defer client.CloseNow()

	ctx := r.Context()
	aiURL := fmt.Sprintf("%s?sessionId=%d&messageId=%d", h.AIBaseURL, sid, mid)
	upstream, _, err := websocket.Dial(ctx, aiURL, &websocket.DialOptions{
		HTTPHeader: http.Header{"X-Internal-API-Key": {h.InternalKey}},
	})
	if err != nil {
		slog.Warn("ws_audio.dial_ai.failed", "err", err, "session_id", sid)
		_ = client.Close(websocket.StatusInternalError, "ai upstream unavailable")
		return
	}
	defer upstream.CloseNow()
	slog.Info("ws_audio.proxy.start", "session_id", sid, "message_id", mid, "user_id", userID, "trace_id", traceID)

	errc := make(chan error, 2)
	// 브라우저 → AI (오디오 업)
	go func() { errc <- copyWS(ctx, client, upstream) }()
	// AI → 브라우저 (자막 다운)
	go func() { errc <- copyWS(ctx, upstream, client) }()

	<-errc // 한쪽이 끝나면 종료
	slog.Info("ws_audio.proxy.end", "session_id", sid, "message_id", mid)
}

// copyWS 는 src 에서 받은 프레임을 dst 로 그대로 전달한다 (타입 보존).
func copyWS(ctx context.Context, src, dst *websocket.Conn) error {
	for {
		typ, data, err := src.Read(ctx)
		if err != nil {
			return err
		}
		if err := dst.Write(ctx, typ, data); err != nil {
			return err
		}
	}
}
