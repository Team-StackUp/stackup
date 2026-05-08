package config

import (
	"testing"
	"time"
)

func TestLoadFromEnv(t *testing.T) {
	t.Setenv("REALTIME_LISTEN_ADDR", ":9090")
	t.Setenv("REALTIME_RABBITMQ_URL", "amqp://x:y@h:1/")
	t.Setenv("REALTIME_LOG_LEVEL", "debug")
	t.Setenv("REALTIME_QUEUE_NAME", "q.test")
	t.Setenv("REALTIME_SSE_PING_INTERVAL", "10s")
	t.Setenv("REALTIME_SSE_SLOW_CONSUMER_TIMEOUT", "2s")
	t.Setenv("REALTIME_SSE_BUFFER_SIZE", "32")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	if cfg.ListenAddr != ":9090" {
		t.Errorf("ListenAddr = %q, want :9090", cfg.ListenAddr)
	}
	if cfg.RabbitMQURL != "amqp://x:y@h:1/" {
		t.Errorf("RabbitMQURL = %q", cfg.RabbitMQURL)
	}
	if cfg.LogLevel != "debug" {
		t.Errorf("LogLevel = %q", cfg.LogLevel)
	}
	if cfg.QueueName != "q.test" {
		t.Errorf("QueueName = %q", cfg.QueueName)
	}
	if cfg.SSEPingInterval != 10*time.Second {
		t.Errorf("SSEPingInterval = %v", cfg.SSEPingInterval)
	}
	if cfg.SSESlowConsumerTimeout != 2*time.Second {
		t.Errorf("SSESlowConsumerTimeout = %v", cfg.SSESlowConsumerTimeout)
	}
	if cfg.SSEBufferSize != 32 {
		t.Errorf("SSEBufferSize = %d", cfg.SSEBufferSize)
	}
}

func TestLoadDefaults(t *testing.T) {
	// Clear all overrides
	for _, k := range []string{
		"REALTIME_LISTEN_ADDR",
		"REALTIME_RABBITMQ_URL",
		"REALTIME_LOG_LEVEL",
		"REALTIME_QUEUE_NAME",
		"REALTIME_SSE_PING_INTERVAL",
		"REALTIME_SSE_SLOW_CONSUMER_TIMEOUT",
		"REALTIME_SSE_BUFFER_SIZE",
	} {
		t.Setenv(k, "")
	}

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	if cfg.ListenAddr != ":8081" {
		t.Errorf("default ListenAddr = %q", cfg.ListenAddr)
	}
	if cfg.QueueName != "q.realtime.session.notify" {
		t.Errorf("default QueueName = %q", cfg.QueueName)
	}
	if cfg.SSEPingInterval != 30*time.Second {
		t.Errorf("default SSEPingInterval = %v", cfg.SSEPingInterval)
	}
	if cfg.SSESlowConsumerTimeout != 5*time.Second {
		t.Errorf("default SSESlowConsumerTimeout = %v", cfg.SSESlowConsumerTimeout)
	}
	if cfg.SSEBufferSize != 16 {
		t.Errorf("default SSEBufferSize = %d", cfg.SSEBufferSize)
	}
}
