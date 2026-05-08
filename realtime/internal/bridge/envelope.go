package bridge

import (
	"encoding/json"
	"errors"
)

type RealtimePayload struct {
	EventType string          `json:"eventType"`
	Data      json.RawMessage `json:"data"`
}

type Context struct {
	UserID    *int64 `json:"userId,omitempty"`
	SessionID *int64 `json:"sessionId,omitempty"`
}

type Envelope struct {
	MessageID   string          `json:"messageId"`
	MessageType string          `json:"messageType"`
	Version     string          `json:"version"`
	TraceID     string          `json:"traceId"`
	PublishedAt string          `json:"publishedAt"`
	Publisher   string          `json:"publisher"`
	Payload     RealtimePayload `json:"payload"`
	Context     Context         `json:"context"`
}

var (
	ErrMissingSessionID = errors.New("envelope.context.sessionId is required")
)

func ParseEnvelope(body []byte) (Envelope, error) {
	var env Envelope
	if err := json.Unmarshal(body, &env); err != nil {
		return Envelope{}, err
	}
	if env.Context.SessionID == nil {
		return Envelope{}, ErrMissingSessionID
	}
	return env, nil
}
