export type Segment = { seq: number; url: string }

// seq 순서로 한 번에 하나씩 재생. play(url) 는 재생 시작(Promise). onEnded() 로 다음 진행.
// reset() 으로 새 질문 시작 시 seq 카운터/대기열 초기화.
export function createSegmentQueue(play: (url: string) => Promise<void>) {
  const pending: Segment[] = []
  let expected = 0
  let busy = false

  function drain() {
    if (busy) return
    const i = pending.findIndex((s) => s.seq === expected)
    if (i === -1) return
    const [seg] = pending.splice(i, 1)
    busy = true
    expected += 1
    void play(seg.url)
  }
  return {
    enqueue(seg: Segment) { pending.push(seg); drain() },
    onEnded() { busy = false; drain() },
    reset() { pending.length = 0; expected = 0; busy = false },
  }
}
