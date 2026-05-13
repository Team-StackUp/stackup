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
	subs   map[int64][]*Subscriber
	nextID atomic.Int64
}

func NewRegistry() *Registry {
	return &Registry{subs: make(map[int64][]*Subscriber)}
}

// Subscribe registers a new subscriber for sessionID with the given channel
// buffer size and returns a *Subscriber. The caller must Unsubscribe when done.
func (r *Registry) Subscribe(sessionID int64, bufferSize int) *Subscriber {
	if bufferSize <= 0 {
		bufferSize = 1
	}
	sub := &Subscriber{
		id: r.nextID.Add(1),
		Ch: make(chan Event, bufferSize),
	}
	r.mu.Lock()
	r.subs[sessionID] = append(r.subs[sessionID], sub)
	r.mu.Unlock()
	return sub
}

func (r *Registry) Unsubscribe(sessionID int64, sub *Subscriber) {
	r.mu.Lock()
	defer r.mu.Unlock()
	list := r.subs[sessionID]
	for i, s := range list {
		if s.id == sub.id {
			r.subs[sessionID] = append(list[:i], list[i+1:]...)
			break
		}
	}
	if len(r.subs[sessionID]) == 0 {
		delete(r.subs, sessionID)
	}
}

// Dispatch sends ev to all subscribers of sessionID. For each subscriber,
// the send waits up to slowTimeout before dropping that subscriber's delivery.
// Returns the number of subscribers that received the event.
func (r *Registry) Dispatch(sessionID int64, ev Event, slowTimeout time.Duration) int {
	r.mu.RLock()
	subs := append([]*Subscriber(nil), r.subs[sessionID]...)
	r.mu.RUnlock()

	delivered := 0
	for _, s := range subs {
		select {
		case s.Ch <- ev:
			delivered++
		case <-time.After(slowTimeout):
			// drop
		}
	}
	return delivered
}
