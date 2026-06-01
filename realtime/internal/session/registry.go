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

// Dispatch sends ev to all subscribers of channel. Each send waits up to
// slowTimeout before dropping that subscriber's delivery. Returns the number
// of subscribers that received the event.
func (r *Registry) Dispatch(channel Channel, ev Event, slowTimeout time.Duration) int {
	r.mu.RLock()
	subs := append([]*Subscriber(nil), r.subs[channel]...)
	r.mu.RUnlock()

	// Reuse a single timer across subscribers. time.After would leak one timer
	// goroutine per (subscriber × event) until slowTimeout elapsed.
	timer := time.NewTimer(slowTimeout)
	defer timer.Stop()

	delivered := 0
	for _, s := range subs {
		if !timer.Stop() {
			select {
			case <-timer.C:
			default:
			}
		}
		timer.Reset(slowTimeout)
		select {
		case s.Ch <- ev:
			delivered++
		case <-timer.C:
			// drop slow consumer's delivery
		}
	}
	return delivered
}
