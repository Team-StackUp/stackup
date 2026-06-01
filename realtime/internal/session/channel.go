package session

// ChannelKind enumerates the subscription channel namespaces.
type ChannelKind string

const (
	ChannelSession  ChannelKind = "session"
	ChannelUser     ChannelKind = "user"
	ChannelDocument ChannelKind = "document"
)

// Channel identifies a fan-out target: a kind plus the resource id.
type Channel struct {
	Kind ChannelKind
	ID   int64
}
