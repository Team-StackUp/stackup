package session

import (
	"sync"
	"testing"
	"time"
)

func TestSubscribeReceivesEvent(t *testing.T) {
	r := NewRegistry()

	sub := r.Subscribe(99, 4)
	defer r.Unsubscribe(99, sub)

	ev := Event{ID: "1", Type: "x", Data: []byte("hi")}
	delivered := r.Dispatch(99, ev, 100*time.Millisecond)
	if delivered != 1 {
		t.Fatalf("delivered = %d, want 1", delivered)
	}

	select {
	case got := <-sub.Ch:
		if got.ID != "1" || got.Type != "x" || string(got.Data) != "hi" {
			t.Errorf("event = %+v", got)
		}
	case <-time.After(100 * time.Millisecond):
		t.Fatal("did not receive event")
	}
}

func TestDispatchToUnknownSessionDelivers0(t *testing.T) {
	r := NewRegistry()

	ev := Event{ID: "1", Type: "x", Data: []byte("hi")}
	if got := r.Dispatch(99, ev, 100*time.Millisecond); got != 0 {
		t.Errorf("delivered = %d, want 0", got)
	}
}

func TestMultipleSubscribersReceive(t *testing.T) {
	r := NewRegistry()
	a := r.Subscribe(7, 4)
	b := r.Subscribe(7, 4)
	defer r.Unsubscribe(7, a)
	defer r.Unsubscribe(7, b)

	if got := r.Dispatch(7, Event{ID: "x"}, 100*time.Millisecond); got != 2 {
		t.Errorf("delivered = %d, want 2", got)
	}
}

func TestUnsubscribeStopsDelivery(t *testing.T) {
	r := NewRegistry()
	sub := r.Subscribe(3, 4)
	r.Unsubscribe(3, sub)

	if got := r.Dispatch(3, Event{ID: "x"}, 100*time.Millisecond); got != 0 {
		t.Errorf("delivered = %d, want 0", got)
	}
}

func TestSlowConsumerDoesNotBlockOthers(t *testing.T) {
	r := NewRegistry()
	slow := r.Subscribe(1, 1)
	fast := r.Subscribe(1, 1)
	defer r.Unsubscribe(1, slow)
	defer r.Unsubscribe(1, fast)

	// Fill slow's buffer.
	r.Dispatch(1, Event{ID: "first"}, 100*time.Millisecond)

	// Drain fast's buffer so it can receive the next message.
	<-fast.Ch

	start := time.Now()
	delivered := r.Dispatch(1, Event{ID: "second"}, 50*time.Millisecond)
	elapsed := time.Since(start)

	// Fast consumer should have received "second", slow should be dropped.
	if delivered != 1 {
		t.Errorf("delivered = %d, want 1 (slow dropped)", delivered)
	}
	if elapsed > 200*time.Millisecond {
		t.Errorf("dispatch blocked %v on slow consumer", elapsed)
	}

	select {
	case got := <-fast.Ch:
		if got.ID != "second" {
			t.Errorf("fast consumer received %v, want second", got.ID)
		}
	default:
		t.Error("fast consumer did not receive any event")
	}
}

func TestConcurrentSubscribeUnsubscribe(t *testing.T) {
	r := NewRegistry()
	var wg sync.WaitGroup
	for i := 0; i < 50; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			s := r.Subscribe(42, 4)
			r.Dispatch(42, Event{ID: "x"}, 50*time.Millisecond)
			r.Unsubscribe(42, s)
		}()
	}
	wg.Wait()
}
