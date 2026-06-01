package bridge

import (
	"testing"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
)

func TestDispatchSessionChannel(t *testing.T) {
	r := session.NewRegistry()
	sub := r.Subscribe(session.Channel{Kind: session.ChannelSession, ID: 99}, 4)
	defer r.Unsubscribe(session.Channel{Kind: session.ChannelSession, ID: 99}, sub)

	d := NewDispatcher(r, 100*time.Millisecond)
	body := []byte(`{"messageId":"m1","messageType":"realtime.session.notify","version":"v1",
	  "traceId":"t1","publishedAt":"2026-06-01T00:00:00Z","publisher":"core",
	  "payload":{"eventType":"session.message","data":{"q":"hi"}},"context":{"sessionId":99}}`)

	res, err := d.Dispatch(body)
	if err != nil {
		t.Fatalf("Dispatch err: %v", err)
	}
	if res.Channel.Kind != session.ChannelSession || res.Channel.ID != 99 || res.Delivered != 1 {
		t.Errorf("res = %+v", res)
	}
	select {
	case got := <-sub.Ch:
		if got.ID != "m1" || got.Type != "session.message" {
			t.Errorf("event = %+v", got)
		}
	case <-time.After(100 * time.Millisecond):
		t.Fatal("did not receive event")
	}
}

func TestDispatchUserChannel(t *testing.T) {
	r := session.NewRegistry()
	sub := r.Subscribe(session.Channel{Kind: session.ChannelUser, ID: 42}, 4)
	defer r.Unsubscribe(session.Channel{Kind: session.ChannelUser, ID: 42}, sub)

	d := NewDispatcher(r, 100*time.Millisecond)
	body := []byte(`{"messageId":"m2","messageType":"realtime.user.notify","version":"v1",
	  "traceId":"t2","publishedAt":"2026-06-01T00:00:00Z","publisher":"core",
	  "payload":{"eventType":"doc.state","data":{}},"context":{"userId":42}}`)

	res, err := d.Dispatch(body)
	if err != nil {
		t.Fatalf("Dispatch err: %v", err)
	}
	if res.Channel.Kind != session.ChannelUser || res.Channel.ID != 42 || res.Delivered != 1 {
		t.Errorf("res = %+v", res)
	}
}

func TestDispatchDocumentChannel(t *testing.T) {
	r := session.NewRegistry()
	d := NewDispatcher(r, 50*time.Millisecond)
	body := []byte(`{"messageId":"m3","messageType":"realtime.document.notify","version":"v1",
	  "traceId":"t3","publishedAt":"2026-06-01T00:00:00Z","publisher":"core",
	  "payload":{"eventType":"doc.state","data":{}},"context":{"documentId":101}}`)

	res, err := d.Dispatch(body)
	if err != nil {
		t.Fatalf("Dispatch err: %v", err)
	}
	if res.Channel.Kind != session.ChannelDocument || res.Channel.ID != 101 || res.Delivered != 0 {
		t.Errorf("res = %+v", res)
	}
}

func TestDispatchMissingChannelIDReturnsError(t *testing.T) {
	r := session.NewRegistry()
	d := NewDispatcher(r, 50*time.Millisecond)
	body := []byte(`{"messageId":"m4","messageType":"realtime.session.notify","version":"v1",
	  "traceId":"t4","publishedAt":"2026-06-01T00:00:00Z","publisher":"core",
	  "payload":{"eventType":"x","data":{}},"context":{}}`)
	if _, err := d.Dispatch(body); err == nil {
		t.Error("expected error for missing sessionId")
	}
}

func TestDispatchUnknownChannelReturnsError(t *testing.T) {
	r := session.NewRegistry()
	d := NewDispatcher(r, 50*time.Millisecond)
	body := []byte(`{"messageId":"m5","messageType":"realtime.bogus.notify","version":"v1",
	  "traceId":"t5","publishedAt":"2026-06-01T00:00:00Z","publisher":"core",
	  "payload":{"eventType":"x","data":{}},"context":{"sessionId":1}}`)
	if _, err := d.Dispatch(body); err == nil {
		t.Error("expected error for unknown channel")
	}
}

func TestDispatchInvalidJSONReturnsError(t *testing.T) {
	r := session.NewRegistry()
	d := NewDispatcher(r, 50*time.Millisecond)
	if _, err := d.Dispatch([]byte("{not json")); err == nil {
		t.Error("expected error")
	}
}
