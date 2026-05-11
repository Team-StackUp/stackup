package messaging

import (
	"context"
	"log/slog"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

type Handler interface {
	Handle(body []byte) error
}

type Consumer struct {
	Connector *Connector
	QueueName string
	Handler   Handler
}

const prefetchCount = 1

// Run blocks, reconnecting on connection loss until ctx is cancelled.
func (c *Consumer) Run(ctx context.Context) error {
	for {
		if err := c.runOnce(ctx); err != nil {
			if ctx.Err() != nil {
				return ctx.Err()
			}
			slog.Warn("amqp.session.reconnecting", "err", err)
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(2 * time.Second):
			}
			continue
		}
		return nil
	}
}

func (c *Consumer) runOnce(ctx context.Context) error {
	conn, err := c.Connector.Dial(ctx)
	if err != nil {
		return err
	}
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		return err
	}
	defer ch.Close()

	if _, err := ch.QueueDeclarePassive(c.QueueName, true, false, false, false, nil); err != nil {
		return err
	}

	if err := ch.Qos(prefetchCount, 0, false); err != nil {
		return err
	}

	deliveries, err := ch.Consume(c.QueueName, "", false, false, false, false, nil)
	if err != nil {
		return err
	}
	slog.Info("amqp.consumer.started", "queue", c.QueueName)

	connCloseCh := conn.NotifyClose(make(chan *amqp.Error, 1))
	chCloseCh := ch.NotifyClose(make(chan *amqp.Error, 1))

	for {
		select {
		case <-ctx.Done():
			slog.Info("amqp.consumer.stopping")
			return ctx.Err()
		case err, ok := <-connCloseCh:
			return normalizeCloseError(err, ok)
		case err, ok := <-chCloseCh:
			return normalizeCloseError(err, ok)
		case d, ok := <-deliveries:
			if !ok {
				return amqp.ErrClosed
			}
			if err := c.Handler.Handle(d.Body); err != nil {
				slog.Warn("amqp.handler.failed", "err", err)
				_ = d.Nack(false, false) // drop (no requeue)
				continue
			}
			_ = d.Ack(false)
		}
	}
}

func normalizeCloseError(err *amqp.Error, ok bool) error {
	if !ok || err == nil {
		return amqp.ErrClosed
	}
	return err
}
