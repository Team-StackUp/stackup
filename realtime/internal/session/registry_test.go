package session

import (
	"sync"
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

// Registry 는 RealTime 서버 전체의 공유 가변 상태다. 운영에서는 AMQP 컨슈머 고루틴이
// Dispatch 하는 동안 HTTP 핸들러들이 Subscribe/Unsubscribe 한다 — 그런데 이 조합을
// 검증하는 테스트가 없었다.
//
// Dispatch 는 의도적으로 락을 놓은 뒤 채널에 쓴다(느린 구독자가 락을 잡고 있으면 다른
// 구독자까지 막히므로). 그 설계 때문에 "복사한 구독자 목록"과 "지금 살아있는 구독자"가
// 어긋나는 창이 생기고, 여기서 어긋남이 자료 경합이 되지 않는지 확인한다.
//
// `-race` 와 함께 돌 때 의미가 있다 (CI 의 `go test -race ./...`).
func TestRegistryConcurrentSubscribeDispatchUnsubscribe(t *testing.T) {
	r := NewRegistry()
	target := ch(ChannelSession, 1)

	const (
		churnGoroutines = 8
		churnIterations = 200
		dispatchers     = 4
	)

	stop := make(chan struct{})
	var dispatchWG, churnWG sync.WaitGroup

	for i := 0; i < dispatchers; i++ {
		dispatchWG.Add(1)
		go func() {
			defer dispatchWG.Done()
			ev := Event{ID: "1", Type: "SESSION_MESSAGE", Data: []byte(`{"a":1}`)}
			for {
				select {
				case <-stop:
					return
				default:
				}
				// 느린 구독자 타임아웃은 짧게 — 버퍼가 찬 구독자 때문에 테스트가 늘어지지 않게.
				r.Dispatch(target, ev, time.Millisecond)
			}
		}()
	}

	for i := 0; i < churnGoroutines; i++ {
		churnWG.Add(1)
		go func() {
			defer churnWG.Done()
			for j := 0; j < churnIterations; j++ {
				sub := r.Subscribe(target, 2)
				// 한 건 정도 읽어 Dispatch 가 항상 타임아웃으로만 끝나지 않게 한다.
				select {
				case <-sub.Ch:
				default:
				}
				r.Unsubscribe(target, sub)
			}
		}()
	}

	churnWG.Wait()
	close(stop)
	dispatchWG.Wait()

	// 모든 구독이 해제됐으면 전달 대상이 남아 있으면 안 된다. id 기반 제거가 churn 중
	// 어긋나면 해제된 구독자가 목록에 남아 여기서 0 이 아니게 된다.
	if delivered := r.Dispatch(target, Event{ID: "x"}, time.Millisecond); delivered != 0 {
		t.Fatalf("expected no subscribers after full unsubscribe, delivered=%d", delivered)
	}
}
