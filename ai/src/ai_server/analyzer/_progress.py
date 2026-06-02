from __future__ import annotations

from collections.abc import Awaitable, Callable

# analyzer 가 단계마다 호출하는 진행 emitter. (phase, message) 만 받으며,
# 실제 발행(user/target/trace 바인딩 + RealTime 직접 publish)은 messaging 레이어가 주입한다.
# analyzer 가 messaging 을 import 하지 않도록(레이어 규칙) 타입만 여기 둔다.
ProgressEmitter = Callable[[str, str], Awaitable[None]]
