.PHONY: build run test test-race fmt lint tidy clean

GO ?= go
PKG ?= ./...

build:
	$(GO) build -o bin/realtime ./cmd/realtime

run:
	$(GO) run ./cmd/realtime

test:
	$(GO) test $(PKG)

# CI 가 도는 것과 같은 형태. 동시성 변경을 했다면 push 전에 이걸로 확인한다.
test-race:
	$(GO) test -race $(PKG)

fmt:
	$(GO) fmt $(PKG)

lint:
	$(GO) vet $(PKG)

tidy:
	$(GO) mod tidy

clean:
	rm -rf bin/
