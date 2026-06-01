package auth

import (
	"context"
	"net/http"
)

type claimsKey struct{}

// Middleware extracts the stream token from the access_token query parameter,
// verifies it, and injects the Claims into the request context.
func Middleware(v *StreamTokenVerifier) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			token := r.URL.Query().Get("access_token")
			if token == "" {
				http.Error(w, "missing access_token", http.StatusUnauthorized)
				return
			}
			claims, err := v.Verify(token)
			if err != nil {
				http.Error(w, "invalid access_token", http.StatusUnauthorized)
				return
			}
			ctx := WithClaims(r.Context(), claims)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// WithClaims injects Claims into ctx.
func WithClaims(ctx context.Context, c Claims) context.Context {
	return context.WithValue(ctx, claimsKey{}, c)
}

// ClaimsFromContext returns the Claims stored in ctx, if any.
func ClaimsFromContext(ctx context.Context) (Claims, bool) {
	c, ok := ctx.Value(claimsKey{}).(Claims)
	return c, ok
}

// WithUserID is kept for tests/wiring that only need the userId.
func WithUserID(ctx context.Context, userID int64) context.Context {
	return WithClaims(ctx, Claims{UserID: userID, ResourceType: "USER", ResourceID: userID})
}

// UserIDFromContext returns the authenticated userId, or 0 if absent.
func UserIDFromContext(ctx context.Context) int64 {
	if c, ok := ClaimsFromContext(ctx); ok {
		return c.UserID
	}
	return 0
}
