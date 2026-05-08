from ai_server.messaging.idempotency import LruIdempotencyStore


def test_first_seen_returns_false() -> None:
    store = LruIdempotencyStore(max_size=4)
    assert store.is_seen_then_mark("a") is False


def test_second_seen_returns_true() -> None:
    store = LruIdempotencyStore(max_size=4)
    store.is_seen_then_mark("a")
    assert store.is_seen_then_mark("a") is True


def test_evicts_oldest_when_full() -> None:
    store = LruIdempotencyStore(max_size=2)
    store.is_seen_then_mark("a")
    store.is_seen_then_mark("b")
    store.is_seen_then_mark("c")  # evicts "a"
    assert store.is_seen_then_mark("b") is True
    assert store.is_seen_then_mark("a") is False


def test_recently_used_avoids_eviction() -> None:
    store = LruIdempotencyStore(max_size=2)
    store.is_seen_then_mark("a")
    store.is_seen_then_mark("b")
    # Touch "a" to make it most recent
    assert store.is_seen_then_mark("a") is True
    # Now adding "c" should evict "b" instead of "a"
    store.is_seen_then_mark("c")
    assert store.is_seen_then_mark("a") is True
    assert store.is_seen_then_mark("b") is False
