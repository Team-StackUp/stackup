package config

import (
	"time"

	"github.com/caarlos0/env/v11"
)

type Config struct {
	ListenAddr             string        `env:"REALTIME_LISTEN_ADDR" envDefault:":38020"`
	RabbitMQURL            string        `env:"REALTIME_RABBITMQ_URL" envDefault:"amqp://stackup:stackup@localhost:38050/"`
	LogLevel               string        `env:"REALTIME_LOG_LEVEL" envDefault:"info"`
	QueueName              string        `env:"REALTIME_QUEUE_NAME" envDefault:"q.realtime.session.notify"`
	JWTSecret              string        `env:"REALTIME_JWT_SECRET" envDefault:"local-development-jwt-secret-must-be-replaced"`
	CoreBaseURL            string        `env:"REALTIME_CORE_BASE_URL" envDefault:"http://localhost:38010"`
	InternalApiKey         string        `env:"REALTIME_INTERNAL_API_KEY" envDefault:"local-development-internal-api-key"`
	WSWriteTimeout         time.Duration `env:"REALTIME_WS_WRITE_TIMEOUT" envDefault:"10s"`
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
