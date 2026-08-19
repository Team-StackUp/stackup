package transport

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/auth"
	"github.com/Team-StackUp/stackup/realtime/internal/session"
	"github.com/go-chi/chi/v5"
)

// document 채널로는 분석 결과(요약·기술스택·문서 경로)가 흐른다 — 남의 이력서 내용이다.
// RealTime 은 DB 를 보지 않으므로, Core 가 소유권을 확인하고 발급한 토큰의 리소스 범위를
// 그대로 강제하는 것이 유일한 소유권 검사다.
func documentRequest(t *testing.T, id string, claims auth.Claims) *http.Request {
	t.Helper()
	req := httptest.NewRequest("GET", "/realtime/stream/documents/"+id, nil)
	rctx := chi.NewRouteContext()
	rctx.URLParams.Add("id", id)
	ctx := context.WithValue(req.Context(), chi.RouteCtxKey, rctx)
	ctx = auth.WithClaims(ctx, claims)
	return req.WithContext(ctx)
}

func newDocumentHandler() http.HandlerFunc {
	return scopedChannel(NewSSEHandler(session.NewRegistry(), 4, time.Hour),
		session.ChannelDocument, "DOCUMENT")
}

func TestDocumentChannelRejectsTokenForAnotherResourceType(t *testing.T) {
	// 세션용 토큰으로 문서 채널을 구독하려는 경우 — 예전엔 그대로 통과했다.
	req := documentRequest(t, "101", auth.Claims{UserID: 1, ResourceType: "SESSION", ResourceID: 101})
	rec := httptest.NewRecorder()

	newDocumentHandler()(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Fatalf("expected 403 for SESSION-scoped token, got %d", rec.Code)
	}
}

func TestDocumentChannelRejectsTokenForAnotherDocument(t *testing.T) {
	// 문서 101 토큰으로 문서 102 를 구독 — id 만 바꿔 남의 분석 결과를 긁는 경로다.
	req := documentRequest(t, "102", auth.Claims{UserID: 1, ResourceType: "DOCUMENT", ResourceID: 101})
	rec := httptest.NewRecorder()

	newDocumentHandler()(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Fatalf("expected 403 for token of another document, got %d", rec.Code)
	}
}

func TestDocumentChannelRejectsMissingClaims(t *testing.T) {
	req := documentRequest(t, "101", auth.Claims{})
	rec := httptest.NewRecorder()

	newDocumentHandler()(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Fatalf("expected 403 when claims are absent, got %d", rec.Code)
	}
}

// 거부만 검증하면 비교를 뒤집어 놔도 통과한다 — 허용 경로도 확인한다.
func TestDocumentChannelAcceptsMatchingToken(t *testing.T) {
	req := documentRequest(t, "101", auth.Claims{UserID: 1, ResourceType: "DOCUMENT", ResourceID: 101})
	ctx, cancel := context.WithCancel(req.Context())
	req = req.WithContext(ctx)
	rec := httptest.NewRecorder()

	done := make(chan struct{})
	go func() {
		newDocumentHandler()(rec, req)
		close(done)
	}()

	// 스트리밍이 시작되므로 잠시 뒤 끊는다.
	time.Sleep(50 * time.Millisecond)
	cancel()
	<-done

	if rec.Code == http.StatusForbidden {
		t.Fatalf("matching token must not be rejected, got %d", rec.Code)
	}
}
