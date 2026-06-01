package transport

import (
	"net/http"
	"strconv"

	"github.com/Team-StackUp/stackup/realtime/internal/auth"
	"github.com/go-chi/chi/v5"
)

// authInject simulates the auth middleware injecting an authenticated userId.
func authInject(userID int64, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ctx := auth.WithUserID(r.Context(), userID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// routeID wraps the handler in a chi router that binds {id} for the WS path.
func routeID(_ string, _ int64, h *WSHandler) http.Handler {
	r := chi.NewRouter()
	r.Get("/realtime/sessions/{id}", func(w http.ResponseWriter, req *http.Request) {
		if _, err := strconv.ParseInt(chi.URLParam(req, "id"), 10, 64); err != nil {
			http.Error(w, "bad id", http.StatusBadRequest)
			return
		}
		h.ServeWS(w, req)
	})
	return r
}
