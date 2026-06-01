package bridge

import (
	"encoding/json"
	"errors"
	"strings"

	"github.com/Team-StackUp/stackup/realtime/internal/session"
)

type RealtimePayload struct {
	EventType string          `json:"eventType"`
	Data      json.RawMessage `json:"data"`
}

type Context struct {
	UserID     *int64 `json:"userId,omitempty"`
	SessionID  *int64 `json:"sessionId,omitempty"`
	DocumentID *int64 `json:"documentId,omitempty"`
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
	ErrMissingChannelID = errors.New("envelope.context is missing the id for its channel")
	ErrUnknownChannel   = errors.New("envelope.messageType is not a known realtime channel")
)

func ParseEnvelope(body []byte) (Envelope, error) {
	var env Envelope
	if err := json.Unmarshal(body, &env); err != nil {
		return Envelope{}, err
	}
	return env, nil
}

// Channel resolves the fan-out target from messageType + context.
// messageType is "realtime.<kind>.notify" (e.g. realtime.user.notify).
func (e Envelope) Channel() (session.Channel, error) {
	parts := strings.Split(e.MessageType, ".")
	if len(parts) < 2 || parts[0] != "realtime" {
		return session.Channel{}, ErrUnknownChannel
	}
	switch session.ChannelKind(parts[1]) {
	case session.ChannelSession:
		if e.Context.SessionID == nil {
			return session.Channel{}, ErrMissingChannelID
		}
		return session.Channel{Kind: session.ChannelSession, ID: *e.Context.SessionID}, nil
	case session.ChannelUser:
		if e.Context.UserID == nil {
			return session.Channel{}, ErrMissingChannelID
		}
		return session.Channel{Kind: session.ChannelUser, ID: *e.Context.UserID}, nil
	case session.ChannelDocument:
		if e.Context.DocumentID == nil {
			return session.Channel{}, ErrMissingChannelID
		}
		return session.Channel{Kind: session.ChannelDocument, ID: *e.Context.DocumentID}, nil
	default:
		return session.Channel{}, ErrUnknownChannel
	}
}
