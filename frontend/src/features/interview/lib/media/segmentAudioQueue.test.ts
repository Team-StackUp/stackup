import { describe, it, expect, vi } from 'vitest'
import { createSegmentQueue } from './segmentAudioQueue'

describe('segmentAudioQueue', () => {
  it('seq 순서대로 하나씩 재생하고 ended 후 다음으로', async () => {
    const played: string[] = []
    const play = vi.fn(async (url: string) => { played.push(url) })
    const q = createSegmentQueue(play)
    q.enqueue({ seq: 1, url: 'u1' })   // 뒤섞여 도착
    q.enqueue({ seq: 0, url: 'u0' })
    await Promise.resolve(); await Promise.resolve()
    expect(played[0]).toBe('u0')       // seq0 먼저
    q.onEnded()
    await Promise.resolve(); await Promise.resolve()
    expect(played[1]).toBe('u1')
  })
  it('reset 후에는 expected seq 가 0 으로 돌아간다', async () => {
    const played: string[] = []
    const q = createSegmentQueue(async (u) => { played.push(u) })
    q.enqueue({ seq: 0, url: 'a' }); await Promise.resolve(); await Promise.resolve()
    q.reset()
    q.enqueue({ seq: 0, url: 'b' }); await Promise.resolve(); await Promise.resolve()
    expect(played).toEqual(['a', 'b'])
  })
})
