import { useParams } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
import { PracticeRunner, TrackPicker } from '@/features/practice'
import type { PracticeTrack } from '@/domain/practice'

const RUNNABLE: PracticeTrack[] = ['frontend', 'backend', 'cs']

function isTrack(value: string | undefined): value is PracticeTrack {
  return RUNNABLE.includes(value as PracticeTrack)
}

export default function PracticePage() {
  const { track } = useParams<{ track: string }>()

  return (
    <div className="flex min-h-svh flex-col bg-bg text-fg">
      <SiteNav />
      <main className="mx-auto w-full max-w-content flex-1 px-6 py-6 lg:px-12">
        {isTrack(track) ? (
          <div className="flex h-[70svh] min-h-120 flex-col overflow-hidden rounded-xl border border-border bg-surface-raised shadow-sm">
            <PracticeRunner track={track} />
          </div>
        ) : track === 'role' ? (
          <TrackPicker />
        ) : (
          <p className="py-16 text-center text-fg-muted">잘못된 면접 유형입니다.</p>
        )}
      </main>
      <SiteFooter />
    </div>
  )
}
