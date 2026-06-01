package transport

import (
	"context"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
)

func TestSSEDocumentChannelReceivesEvent(t *testing.T) {
	reg := session.NewRegistry()
	h := NewSSEHandler(reg, 4, time.Hour)

	req := httptest.NewRequest("GET", "/realtime/stream/documents/101", nil)
	ctx, cancel := context.WithCancel(req.Context())
	req = req.WithContext(ctx)
	rec := httptest.NewRecorder()

	done := make(chan struct{})
	go func() {
		h.ServeChannel(rec, req, session.Channel{Kind: session.ChannelDocument, ID: 101})
		close(done)
	}()

	// allow the handler to subscribe before dispatching
	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		if reg.Dispatch(session.Channel{Kind: session.ChannelDocument, ID: 101},
			session.Event{ID: "m1", Type: "doc.state", Data: []byte(`{"x":1}`)}, 200*time.Millisecond) == 1 {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	time.Sleep(50 * time.Millisecond)
	cancel()
	<-done

	body := rec.Body.String()
	if !strings.Contains(body, "event: doc.state") || !strings.Contains(body, "id: m1") {
		t.Errorf("body missing event frame:\n%s", body)
	}
}
