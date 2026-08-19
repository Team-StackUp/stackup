package transport

import (
	"log/slog"
	"net/http"
	"strconv"

	"github.com/Team-StackUp/stackup/realtime/internal/auth"
	"github.com/Team-StackUp/stackup/realtime/internal/session"
	"github.com/Team-StackUp/stackup/realtime/internal/trace"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

func NewRouter(sse *SSEHandler, ws *WSHandler, audio *WSAudioHandler, verifier *auth.StreamTokenVerifier) http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.Recoverer)
	r.Use(trace.Middleware)
	r.Use(loggingMiddleware)

	r.Get("/health", HealthHandler)

	r.Group(func(pr chi.Router) {
		pr.Use(auth.Middleware(verifier))

		pr.Get("/realtime/stream/me", func(w http.ResponseWriter, req *http.Request) {
			c, _ := auth.ClaimsFromContext(req.Context())
			if c.ResourceType != "USER" {
				http.Error(w, "token resource mismatch", http.StatusForbidden)
				return
			}
			sse.ServeChannel(w, req, session.Channel{Kind: session.ChannelUser, ID: c.UserID})
		})
		// 이 채널로는 분석 결과(요약·기술스택·문서 경로)가 흐른다 — 남의 이력서 내용이다.
		// 세션 채널과 같은 방식으로 토큰의 리소스 범위를 강제한다. 검증이 없으면 유효한
		// 스트림 토큰을 가진 누구나 document id 를 바꿔가며 타인의 분석 결과를 받아볼 수 있다.
		pr.Get("/realtime/stream/documents/{id}", scopedChannel(sse, session.ChannelDocument, "DOCUMENT"))
		pr.Get("/realtime/stream/sessions/{id}", func(w http.ResponseWriter, req *http.Request) {
			id, err := strconv.ParseInt(chi.URLParam(req, "id"), 10, 64)
			if err != nil || id <= 0 {
				http.Error(w, "invalid id", http.StatusBadRequest)
				return
			}
			c, _ := auth.ClaimsFromContext(req.Context())
			if c.ResourceType != "SESSION" || c.ResourceID != id {
				http.Error(w, "token resource mismatch", http.StatusForbidden)
				return
			}
			sse.ServeChannel(w, req, session.Channel{Kind: session.ChannelSession, ID: id})
		})
		pr.Get("/realtime/sessions/{id}", func(w http.ResponseWriter, req *http.Request) {
			id, err := strconv.ParseInt(chi.URLParam(req, "id"), 10, 64)
			if err != nil || id <= 0 {
				http.Error(w, "invalid id", http.StatusBadRequest)
				return
			}
			c, _ := auth.ClaimsFromContext(req.Context())
			if c.ResourceType != "SESSION" || c.ResourceID != id {
				http.Error(w, "token resource mismatch", http.StatusForbidden)
				return
			}
			ws.ServeWS(w, req)
		})
		pr.Get("/realtime/sessions/{id}/audio", func(w http.ResponseWriter, req *http.Request) {
			id, err := strconv.ParseInt(chi.URLParam(req, "id"), 10, 64)
			if err != nil || id <= 0 {
				http.Error(w, "invalid id", http.StatusBadRequest)
				return
			}
			c, _ := auth.ClaimsFromContext(req.Context())
			if c.ResourceType != "SESSION" || c.ResourceID != id {
				http.Error(w, "token resource mismatch", http.StatusForbidden)
				return
			}
			audio.ServeAudioWS(w, req)
		})
	})

	return r
}

// scopedChannel serves an SSE channel only to tokens minted for that exact resource.
// Core issues the token after checking ownership, so matching the claim here is the
// only ownership check RealTime can make (it has no database access).
func scopedChannel(sse *SSEHandler, kind session.ChannelKind, resourceType string) http.HandlerFunc {
	return func(w http.ResponseWriter, req *http.Request) {
		id, err := strconv.ParseInt(chi.URLParam(req, "id"), 10, 64)
		if err != nil || id <= 0 {
			http.Error(w, "invalid id", http.StatusBadRequest)
			return
		}
		c, _ := auth.ClaimsFromContext(req.Context())
		if c.ResourceType != resourceType || c.ResourceID != id {
			http.Error(w, "token resource mismatch", http.StatusForbidden)
			return
		}
		sse.ServeChannel(w, req, session.Channel{Kind: kind, ID: id})
	}
}

func loggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		traceID := trace.FromContext(r.Context())
		slog.Info("http.request", "method", r.Method, "path", r.URL.Path, "trace_id", traceID)
		next.ServeHTTP(w, r)
	})
}
