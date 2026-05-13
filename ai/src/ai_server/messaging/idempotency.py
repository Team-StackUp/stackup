from collections import OrderedDict
from threading import Lock


class LruIdempotencyStore:
    """Thread-safe LRU set for messageId-based idempotency.

    `is_seen_then_mark(key)` returns True if the key was already present
    (caller should ACK + skip), or False if it's new (record it and process).
    """

    def __init__(self, max_size: int) -> None:
        if max_size <= 0:
            raise ValueError("max_size must be > 0")
        self._max_size = max_size
        self._store: OrderedDict[str, None] = OrderedDict()
        self._lock = Lock()

    def is_seen_then_mark(self, key: str) -> bool:
        with self._lock:
            if key in self._store:
                self._store.move_to_end(key)
                return True
            self._store[key] = None
            if len(self._store) > self._max_size:
                self._store.popitem(last=False)
            return False
