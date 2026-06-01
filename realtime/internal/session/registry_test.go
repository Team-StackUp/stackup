package session

import (
	"testing"
	"time"
)

func ch(kind ChannelKind, id int64) Channel { return Channel{Kind: kind, ID: id} }

func TestDispatchDeliversToMatchingChannelOnly(t *testing.T) {
	r := NewRegistry()
	sessionSub := r.Subscribe(ch(ChannelSession, 99), 4)
	userSub := r.Subscribe(ch(ChannelUser, 99), 4)
	defer r.Unsubscribe(ch(ChannelSession, 99), sessionSub)
	defer r.Unsubscribe(ch(ChannelUser, 99), userSub)

	n := r.Dispatch(ch(ChannelSession, 99), Event{ID: "m1", Type: "x", Data: []byte("{}")}, 100*time.Millisecond)
	if n != 1 {
		t.Fatalf("delivered = %d, want 1", n)
	}

	select {
	case got := <-sessionSub.Ch:
		if got.ID != "m1" {
			t.Errorf("session event = %+v", got)
		}
	case <-time.After(100 * time.Millisecond):
		t.Fatal("session sub did not receive event")
	}

	select {
	case got := <-userSub.Ch:
		t.Fatalf("user sub should not receive session event, got %+v", got)
	case <-time.After(50 * time.Millisecond):
	}
}

func TestUnsubscribeRemovesChannelEntry(t *testing.T) {
	r := NewRegistry()
	sub := r.Subscribe(ch(ChannelDocument, 7), 1)
	r.Unsubscribe(ch(ChannelDocument, 7), sub)

	if n := r.Dispatch(ch(ChannelDocument, 7), Event{ID: "m", Type: "x", Data: []byte("{}")}, 50*time.Millisecond); n != 0 {
		t.Errorf("delivered = %d, want 0 after unsubscribe", n)
	}
}
