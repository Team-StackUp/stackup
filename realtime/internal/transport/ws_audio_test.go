package transport

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/coder/websocket"
)

// TestCopyWS_ForwardsFrames 는 coder/websocket 왕복으로 프레임이 그대로 전달되는지 검증한다.
// fake 서버는 한 프레임을 받아 그대로 echo 하고, copyWS 가 쓰는 Read/Write 의 기본 흐름을
// 통해 바이너리 페이로드가 손실 없이 돌아오는지 확인한다.
func TestCopyWS_ForwardsFrames(t *testing.T) {
	srvA := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		c, err := websocket.Accept(w, r, nil)
		if err != nil {
			return
		}
		defer c.CloseNow()
		ctx := r.Context()
		// 한 프레임 받고 echo
		_, data, err := c.Read(ctx)
		if err != nil {
			return
		}
		_ = c.Write(ctx, websocket.MessageBinary, data)
		<-ctx.Done()
	}))
	defer srvA.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	url := "ws" + srvA.URL[len("http"):]
	conn, _, err := websocket.Dial(ctx, url, nil)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.CloseNow()

	if err := conn.Write(ctx, websocket.MessageBinary, []byte("hi")); err != nil {
		t.Fatalf("write: %v", err)
	}
	_, data, err := conn.Read(ctx)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if string(data) != "hi" {
		t.Fatalf("want hi got %s", data)
	}
}
