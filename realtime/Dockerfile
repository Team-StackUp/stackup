# syntax=docker/dockerfile:1.7
FROM golang:1.26-alpine AS builder
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags='-s -w' -o /out/realtime ./cmd/realtime

FROM alpine:3.20
RUN apk add --no-cache wget ca-certificates && \
    addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder /out/realtime /app/realtime
EXPOSE 38020
USER app:app
ENTRYPOINT ["/app/realtime"]
