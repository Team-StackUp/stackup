package messaging

import (
	"context"
	"log/slog"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

type Connector struct {
	URL     string
	Backoff time.Duration
}

func NewConnector(url string) *Connector {
	return &Connector{URL: url, Backoff: 2 * time.Second}
}

// Dial blocks until a connection is established or ctx is cancelled.
// Reconnection should be handled by the caller (see Consumer.Run).
func (c *Connector) Dial(ctx context.Context) (*amqp.Connection, error) {
	for {
		conn, err := amqp.Dial(c.URL)
		if err == nil {
			return conn, nil
		}
		slog.Warn("amqp.dial.retry", "err", err, "backoff", c.Backoff)
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-time.After(c.Backoff):
		}
	}
}
