package auth

import (
	"context"
	"net/http"
)

type ctxKey struct{}

// Middleware extracts the stream token from the access_token query parameter,
// verifies it, and injects the userId into the request context.
func Middleware(v *StreamTokenVerifier) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			token := r.URL.Query().Get("access_token")
			if token == "" {
				http.Error(w, "missing access_token", http.StatusUnauthorized)
				return
			}
			userID, err := v.Verify(token)
			if err != nil {
				http.Error(w, "invalid access_token", http.StatusUnauthorized)
				return
			}
			ctx := context.WithValue(r.Context(), ctxKey{}, userID)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// UserIDFromContext returns the authenticated userId, or 0 if absent.
func UserIDFromContext(ctx context.Context) int64 {
	if v, ok := ctx.Value(ctxKey{}).(int64); ok {
		return v
	}
	return 0
}
