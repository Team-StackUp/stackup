package bridge

import (
	"testing"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
)

func TestDispatchValidEnvelope(t *testing.T) {
	r := session.NewRegistry()
	sub := r.Subscribe(99, 4)
	defer r.Unsubscribe(99, sub)

	d := NewDispatcher(r, 100*time.Millisecond)

	body := []byte(`{
      "messageId":"m1","messageType":"realtime.session.notify","version":"v1",
      "traceId":"t1","publishedAt":"2026-05-08T00:00:00Z","publisher":"core",
      "payload":{"eventType":"question.created","data":{"q":"hello"}},
      "context":{"sessionId":99}
    }`)

	res, err := d.Dispatch(body)
	if err != nil {
		t.Fatalf("Dispatch err: %v", err)
	}
	if res.SessionID != 99 || res.Delivered != 1 {
		t.Errorf("res = %+v", res)
	}

	select {
	case got := <-sub.Ch:
		if got.ID != "m1" || got.Type != "question.created" {
			t.Errorf("event = %+v", got)
		}
	case <-time.After(100 * time.Millisecond):
		t.Fatal("did not receive event")
	}
}

func TestDispatchUnknownSessionDelivers0(t *testing.T) {
	r := session.NewRegistry()
	d := NewDispatcher(r, 50*time.Millisecond)

	body := []byte(`{
      "messageId":"m2","messageType":"realtime.session.notify","version":"v1",
      "traceId":"t2","publishedAt":"2026-05-08T00:00:00Z","publisher":"core",
      "payload":{"eventType":"x","data":{}},
      "context":{"sessionId":404}
    }`)

	res, err := d.Dispatch(body)
	if err != nil {
		t.Fatalf("Dispatch err: %v", err)
	}
	if res.Delivered != 0 {
		t.Errorf("delivered = %d", res.Delivered)
	}
}

func TestDispatchInvalidJSONReturnsError(t *testing.T) {
	r := session.NewRegistry()
	d := NewDispatcher(r, 50*time.Millisecond)
	if _, err := d.Dispatch([]byte("{not json")); err == nil {
		t.Error("expected error")
	}
}

func TestDispatchMissingSessionIdReturnsError(t *testing.T) {
	r := session.NewRegistry()
	d := NewDispatcher(r, 50*time.Millisecond)

	body := []byte(`{
      "messageId":"m3","messageType":"realtime.session.notify","version":"v1",
      "traceId":"t3","publishedAt":"2026-05-08T00:00:00Z","publisher":"core",
      "payload":{"eventType":"x","data":{}},
      "context":{}
    }`)

	if _, err := d.Dispatch(body); err == nil {
		t.Error("expected error")
	}
}
