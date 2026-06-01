package bridge

import (
	"encoding/json"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
)

type DispatchResult struct {
	Channel   session.Channel
	Delivered int
	Envelope  Envelope
}

type Dispatcher struct {
	registry    *session.Registry
	slowTimeout time.Duration
}

func NewDispatcher(r *session.Registry, slowTimeout time.Duration) *Dispatcher {
	return &Dispatcher{registry: r, slowTimeout: slowTimeout}
}

func (d *Dispatcher) Dispatch(body []byte) (DispatchResult, error) {
	env, err := ParseEnvelope(body)
	if err != nil {
		return DispatchResult{}, err
	}

	channel, err := env.Channel()
	if err != nil {
		return DispatchResult{}, err
	}

	data, _ := json.Marshal(map[string]any{
		"data":    env.Payload.Data,
		"traceId": env.TraceID,
	})

	delivered := d.registry.Dispatch(channel, session.Event{
		ID:   env.MessageID,
		Type: env.Payload.EventType,
		Data: data,
	}, d.slowTimeout)

	return DispatchResult{Channel: channel, Delivered: delivered, Envelope: env}, nil
}
