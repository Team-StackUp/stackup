package transport

import "testing"

// AI 는 contentType 으로 STT 세션의 디코더를 고른다. RealTime 이 넘기지 않으면
// 무엇을 보내든 audio/webm 으로 가정되어 webm 이 아닌 브라우저(Safari=mp4)에서 틀어진다.
func TestResolveAudioContentType(t *testing.T) {
	cases := []struct {
		name string
		in   string
		want string
	}{
		{"코덱 파라미터를 떼고 base MIME 만", "audio/webm;codecs=opus", "audio/webm"},
		{"대문자도 정규화", "AUDIO/MP4", "audio/mp4"},
		{"앞뒤 공백 허용", "  audio/ogg  ", "audio/ogg"},
		{"허용 목록의 다른 타입", "audio/mpeg", "audio/mpeg"},
		{"빈 값이면 기존 동작과 같은 기본값", "", "audio/webm"},
		{"허용 목록 밖은 기본값", "audio/flac", "audio/webm"},
		// 사용자 제어 값이므로 임의 문자열이 업스트림 URL 로 새어 나가면 안 된다.
		{"주입 시도도 기본값", "audio/webm&apiKey=leak", "audio/webm"},
		{"완전히 다른 값", "text/html", "audio/webm"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := resolveAudioContentType(c.in); got != c.want {
				t.Fatalf("resolveAudioContentType(%q) = %q, want %q", c.in, got, c.want)
			}
		})
	}
}

func TestBuildAIStreamURL(t *testing.T) {
	got := buildAIStreamURL("ws://ai:8000/internal/voice/stream", 7, 42, "audio/mp4")
	want := "ws://ai:8000/internal/voice/stream?sessionId=7&messageId=42&contentType=audio%2Fmp4"
	if got != want {
		t.Fatalf("buildAIStreamURL = %q, want %q", got, want)
	}
}

// 슬래시가 이스케이프되지 않으면 AI 쪽 쿼리 파싱이 어긋난다.
func TestBuildAIStreamURLEscapesContentType(t *testing.T) {
	got := buildAIStreamURL("ws://ai:8000/s", 1, 2, "audio/webm")
	if want := "contentType=audio%2Fwebm"; !contains(got, want) {
		t.Fatalf("expected %q in %q", want, got)
	}
}

func contains(s, sub string) bool {
	return len(s) >= len(sub) && (func() bool {
		for i := 0; i+len(sub) <= len(s); i++ {
			if s[i:i+len(sub)] == sub {
				return true
			}
		}
		return false
	})()
}
