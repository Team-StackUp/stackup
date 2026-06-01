package core

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/Team-StackUp/stackup/realtime/internal/trace"
)

// AnswerSubmitter is the subset of Core needed by the WS handler — facilitates testing.
type AnswerSubmitter interface {
	SubmitAnswer(ctx context.Context, sessionID, userID int64, content, idempotencyKey string) error
}

// Client calls Core internal REST endpoints. RealTime never touches PG or publishes to MQ;
// this is the only reverse path (WS answer -> Core).
type Client struct {
	baseURL    string
	apiKey     string
	httpClient *http.Client
}

func NewClient(baseURL, apiKey string, timeout time.Duration) *Client {
	return &Client{baseURL: baseURL, apiKey: apiKey, httpClient: &http.Client{Timeout: timeout}}
}

type answerBody struct {
	UserID         int64  `json:"userId"`
	Content        string `json:"content"`
	IdempotencyKey string `json:"idempotencyKey,omitempty"`
}

func (c *Client) SubmitAnswer(ctx context.Context, sessionID, userID int64, content, idempotencyKey string) error {
	body, err := json.Marshal(answerBody{UserID: userID, Content: content, IdempotencyKey: idempotencyKey})
	if err != nil {
		return err
	}
	url := fmt.Sprintf("%s/api/internal/sessions/%d/messages", c.baseURL, sessionID)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-API-Key", c.apiKey)
	if tid := trace.FromContext(ctx); tid != "" {
		req.Header.Set(trace.HeaderName, tid)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("core submit answer: status %d", resp.StatusCode)
	}
	return nil
}
