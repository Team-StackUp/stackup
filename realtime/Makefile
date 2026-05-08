.PHONY: build run test fmt lint tidy clean

GO ?= go
PKG ?= ./...

build:
	$(GO) build -o bin/realtime ./cmd/realtime

run:
	$(GO) run ./cmd/realtime

test:
	$(GO) test $(PKG)

fmt:
	$(GO) fmt $(PKG)

lint:
	$(GO) vet $(PKG)

tidy:
	$(GO) mod tidy

clean:
	rm -rf bin/
