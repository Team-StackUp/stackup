from __future__ import annotations

import re

# 종결: 문장부호(. ! ? …) 뒤에 공백/끝, 또는 줄바꿈.
_SENTENCE_END = re.compile(r".*?(?:[.!?…](?=[ \t]|$)|\n)", re.DOTALL)


def next_sentences(text: str, consumed: int) -> tuple[list[str], int]:
    """text[consumed:] 에서 '완성된' 문장들만 떼어 반환하고 새 consumed 를 돌려준다.

    미완성(종결부호 없는) 꼬리는 남겨 다음 호출에서 이어 처리한다.

    반환 정책:
    - 완성 문장이 없으면 ([], consumed) 반환.
    - 완성 문장이 1개이거나 마지막 완성 문장이 '\\n' 으로 끝나면 전부 반환.
    - 그 외(마지막 완성 문장이 공백 앞 구두점으로만 끝나고 2개 이상 존재)에는
      마지막 문장을 버퍼에 남기고 나머지만 반환한다. 이는 스트리밍 중 아직
      다음 토큰이 올 수 있는 경계를 보수적으로 처리하기 위함이다.
    """
    rest = text[consumed:]

    # 각 완성 문장의 (stripped 텍스트, 줄바꿈 종결 여부, 매치 끝 위치) 수집
    segments: list[tuple[str, bool, int]] = []
    pos = 0
    for m in _SENTENCE_END.finditer(rest):
        chunk = rest[pos : m.end()].strip()
        is_newline = m.group().endswith("\n")
        if chunk:
            segments.append((chunk, is_newline, m.end()))
        pos = m.end()

    if not segments:
        return [], consumed

    # 마지막 문장이 줄바꿈 종결이거나 완성 문장이 1개뿐이면 전부 방출
    last_is_newline = segments[-1][1]
    if last_is_newline or len(segments) == 1:
        out = [s for s, _, _ in segments]
        new_consumed = consumed + segments[-1][2]
    else:
        # 마지막 space-terminated 문장은 버퍼로 남김
        to_emit = segments[:-1]
        out = [s for s, _, _ in to_emit]
        new_consumed = consumed + to_emit[-1][2]

    return out, new_consumed
