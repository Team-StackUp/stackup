package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/auth"
	"github.com/Team-StackUp/stackup/realtime/internal/bridge"
	"github.com/Team-StackUp/stackup/realtime/internal/config"
	"github.com/Team-StackUp/stackup/realtime/internal/messaging"
	"github.com/Team-StackUp/stackup/realtime/internal/session"
	"github.com/Team-StackUp/stackup/realtime/internal/transport"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("config.load.failed", "err", err)
		os.Exit(1)
	}
	configureLogger(cfg.LogLevel)

	registry := session.NewRegistry()
	dispatcher := bridge.NewDispatcher(registry, cfg.SSESlowConsumerTimeout)
	consumer := &messaging.Consumer{
		Connector: messaging.NewConnector(cfg.RabbitMQURL),
		QueueName: cfg.QueueName,
		Handler:   handlerAdapter{d: dispatcher},
	}

	sseHandler := transport.NewSSEHandler(registry, cfg.SSEBufferSize, cfg.SSEPingInterval)
	verifier := auth.NewStreamTokenVerifier(cfg.JWTSecret)
	router := transport.NewRouter(sseHandler, verifier)

	srv := &http.Server{
		Addr:              cfg.ListenAddr,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		slog.Info("http.listen", "addr", cfg.ListenAddr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("http.serve.failed", "err", err)
			stop()
		}
	}()

	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := consumer.Run(ctx); err != nil && !errors.Is(err, context.Canceled) {
			slog.Error("amqp.consumer.failed", "err", err)
			stop()
		}
	}()

	<-ctx.Done()
	slog.Info("shutdown.start")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutdownCtx)

	wg.Wait()
	slog.Info("shutdown.done")
}

type handlerAdapter struct {
	d *bridge.Dispatcher
}

func (h handlerAdapter) Handle(body []byte) error {
	res, err := h.d.Dispatch(body)
	if err != nil {
		slog.Warn("dispatch.failed", "err", err)
		return nil // drop, do not requeue
	}
	slog.Info("dispatched",
		"channel_kind", res.Channel.Kind,
		"channel_id", res.Channel.ID,
		"delivered", res.Delivered,
		"trace_id", res.Envelope.TraceID,
		"event_type", res.Envelope.Payload.EventType,
	)
	return nil
}

func configureLogger(level string) {
	var lvl slog.Level
	switch strings.ToLower(level) {
	case "debug":
		lvl = slog.LevelDebug
	case "warn":
		lvl = slog.LevelWarn
	case "error":
		lvl = slog.LevelError
	default:
		lvl = slog.LevelInfo
	}
	h := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: lvl})
	slog.SetDefault(slog.New(h))
}
