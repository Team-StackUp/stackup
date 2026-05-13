package config

import (
	"time"

	"github.com/caarlos0/env/v11"
)

type Config struct {
	ListenAddr             string        `env:"REALTIME_LISTEN_ADDR" envDefault:":8081"`
	RabbitMQURL            string        `env:"REALTIME_RABBITMQ_URL" envDefault:"amqp://stackup:stackup@localhost:5672/"`
	LogLevel               string        `env:"REALTIME_LOG_LEVEL" envDefault:"info"`
	QueueName              string        `env:"REALTIME_QUEUE_NAME" envDefault:"q.realtime.session.notify"`
	SSEPingInterval        time.Duration `env:"REALTIME_SSE_PING_INTERVAL" envDefault:"30s"`
	SSESlowConsumerTimeout time.Duration `env:"REALTIME_SSE_SLOW_CONSUMER_TIMEOUT" envDefault:"5s"`
	SSEBufferSize          int           `env:"REALTIME_SSE_BUFFER_SIZE" envDefault:"16"`
}

func Load() (Config, error) {
	cfg := Config{}
	if err := env.Parse(&cfg); err != nil {
		return Config{}, err
	}
	return cfg, nil
}
