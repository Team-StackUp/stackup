package session

import (
	"sync"
	"sync/atomic"
	"time"
)

type Event struct {
	ID   string
	Type string
	Data []byte
}

type Subscriber struct {
	id int64
	Ch chan Event
}

type Registry struct {
	mu     sync.RWMutex
	subs   map[Channel][]*Subscriber
	nextID atomic.Int64
}

func NewRegistry() *Registry {
	return &Registry{subs: make(map[Channel][]*Subscriber)}
}

// Subscribe registers a new subscriber for the channel with the given buffer
// size. The caller must Unsubscribe when done.
func (r *Registry) Subscribe(channel Channel, bufferSize int) *Subscriber {
	if bufferSize <= 0 {
		bufferSize = 1
	}
	sub := &Subscriber{
		id: r.nextID.Add(1),
		Ch: make(chan Event, bufferSize),
	}
	r.mu.Lock()
	r.subs[channel] = append(r.subs[channel], sub)
	r.mu.Unlock()
	return sub
}

func (r *Registry) Unsubscribe(channel Channel, sub *Subscriber) {
	r.mu.Lock()
	defer r.mu.Unlock()
	list := r.subs[channel]
	for i, s := range list {
		if s.id == sub.id {
			r.subs[channel] = append(list[:i], list[i+1:]...)
			break
		}
	}
	if len(r.subs[channel]) == 0 {
		delete(r.subs, channel)
	}
}

// Dispatch sends ev to all subscribers of channel, giving up on stalled ones.
// Returns the number of subscribers that received the event.
//
// slowTimeout is a budget for the whole call, not per subscriber. It used to be
// reset for each one, so K stalled subscribers blocked the caller for K ×
// slowTimeout. That matters because the AMQP consumer is single-threaded with
// prefetch=1 (messaging.Consumer): whatever blocks here delays every *other*
// session's events too, not just the slow one's. Three stalled tabs on one
// channel could stall the whole fan-out for 15s at the default 5s.
//
// With a shared deadline the worst case is one slowTimeout per message no
// matter how many subscribers are stuck.
func (r *Registry) Dispatch(channel Channel, ev Event, slowTimeout time.Duration) int {
	r.mu.RLock()
	subs := append([]*Subscriber(nil), r.subs[channel]...)
	r.mu.RUnlock()

	if len(subs) == 0 {
		return 0
	}

	// One timer for the whole dispatch. time.After would leak a timer goroutine
	// per (subscriber × event) until it fired.
	timer := time.NewTimer(slowTimeout)
	defer timer.Stop()

	delivered := 0
	budgetLeft := true
	for _, s := range subs {
		if !budgetLeft {
			// Budget already spent by an earlier stalled subscriber. Still try a
			// non-blocking send — a healthy subscriber must not lose an event
			// because someone before it in the list was stuck.
			select {
			case s.Ch <- ev:
				delivered++
			default:
			}
			continue
		}
		select {
		case s.Ch <- ev:
			delivered++
		case <-timer.C:
			budgetLeft = false
		}
	}
	return delivered
}
