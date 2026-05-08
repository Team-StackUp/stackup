package transport

import (
	"log/slog"
	"net/http"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
	"github.com/Team-StackUp/stackup/realtime/internal/trace"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

func NewRouter(reg *session.Registry, sse *SSEHandler) http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.Recoverer)
	r.Use(trace.Middleware)
	r.Use(loggingMiddleware)

	r.Get("/health", HealthHandler)
	r.Get("/realtime/sessions/{id}", sse.ServeHTTP)

	return r
}

func loggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		traceID := trace.FromContext(r.Context())
		slog.Info("http.request", "method", r.Method, "path", r.URL.Path, "trace_id", traceID)
		next.ServeHTTP(w, r)
	})
}
