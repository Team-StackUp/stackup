package transport

import (
	"context"
	"encoding/json"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
	"github.com/coder/websocket"
)

type fakeSubmitter struct {
	mu    sync.Mutex
	calls []submitCall
}
type submitCall struct {
	sessionID, userID int64
	content           string
}

func (f *fakeSubmitter) SubmitAnswer(_ context.Context, sessionID, userID int64, content, _ string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.calls = append(f.calls, submitCall{sessionID, userID, content})
	return nil
}

func TestWSPushAndAnswer(t *testing.T) {
	reg := session.NewRegistry()
	sub := &fakeSubmitter{}
	h := NewWSHandler(reg, sub, 2*time.Second)

	srv := httptest.NewServer(authInject(7, routeID("id", 99, h)))
	defer srv.Close()

	wsURL := "ws" + strings.TrimPrefix(srv.URL, "http")
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	conn, _, err := websocket.Dial(ctx, wsURL+"/realtime/sessions/99", nil)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close(websocket.StatusNormalClosure, "")

	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if reg.Dispatch(session.Channel{Kind: session.ChannelSession, ID: 99},
			session.Event{ID: "m1", Type: "session.message", Data: []byte(`{"messageId":503}`)},
			500*time.Millisecond) == 1 {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	_, data, err := conn.Read(ctx)
	if err != nil {
		t.Fatalf("read push: %v", err)
	}
	var frame map[string]any
	if err := json.Unmarshal(data, &frame); err != nil {
		t.Fatalf("frame not json: %v", err)
	}
	if frame["event"] != "session.message" || frame["id"] != "m1" {
		t.Errorf("frame = %s", data)
	}

	answer := `{"type":"answer","content":"내 답변","idempotencyKey":"k1"}`
	if err := conn.Write(ctx, websocket.MessageText, []byte(answer)); err != nil {
		t.Fatalf("write answer: %v", err)
	}

	deadline = time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		sub.mu.Lock()
		n := len(sub.calls)
		sub.mu.Unlock()
		if n == 1 {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	sub.mu.Lock()
	defer sub.mu.Unlock()
	if len(sub.calls) != 1 || sub.calls[0].sessionID != 99 || sub.calls[0].userID != 7 || sub.calls[0].content != "내 답변" {
		t.Errorf("submit calls = %+v", sub.calls)
	}
}
