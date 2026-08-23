import { memo, useState } from 'react'
import { Markdown, QueryError } from '@/shared/ui'
import { useFeedbackReport } from '../model/useFeedbackReport'

// AI 학습 리포트(마크다운) 열람/다운로드. reportFilePath 가 있을 때만 렌더 —
// 저장 실패 폴백(None)·구버전 피드백·공개 공유 응답(키 미노출)에서는 진입점 자체가 없다.
// PDF 캡처(reportRef) 바깥에 두는 것이 의도 — 리포트 파일은 자체 산출물이라 중복 캡처하지 않는다.
// memo: 부모(FeedbackReport)의 잦은 상태 변화(복사 토글·공유 pending·PDF 생성)마다
// react-markdown 이 전체 리포트를 재파싱하지 않게 한다 — props 는 원시값 2개뿐이다.
export const FeedbackAiReport = memo(function FeedbackAiReport({
  sessionId,
  reportFilePath,
}: {
  sessionId: number
  reportFilePath?: string | null
}) {
  // 열람 요청 시에만 로드 — 리포트 화면 진입만으로 원문 fetch 를 낭비하지 않는다(분석 원문 보기와 동일).
  const [open, setOpen] = useState(false)
  const report = useFeedbackReport(sessionId, open)

  if (!reportFilePath) return null

  const handleDownloadMd = () => {
    if (!report.data) return
    const blob = new Blob([report.data], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '면접피드백리포트.md'
    // Firefox 등은 DOM 에 없는 앵커의 click 다운로드를 무시할 수 있다.
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  return (
    <section className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <button
          type="button"
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
          className="text-caption font-medium text-primary-fg hover:underline"
        >
          {open ? 'AI 학습 리포트 접기' : 'AI 학습 리포트 보기'}
        </button>
        {open && report.data && (
          <button
            type="button"
            onClick={handleDownloadMd}
            className="text-caption font-medium text-fg-muted hover:text-fg hover:underline"
          >
            .md 다운로드
          </button>
        )}
      </div>
      {open &&
        (report.isPending ? (
          <p className="text-caption text-fg-subtle">리포트를 불러오는 중…</p>
        ) : report.isError ? (
          <QueryError
            message="AI 학습 리포트를 불러오지 못했습니다."
            onRetry={() => report.refetch()}
          />
        ) : (
          <div className="max-w-readable rounded-lg border border-border bg-surface-raised p-4">
            <Markdown className="text-fg">{report.data ?? ''}</Markdown>
          </div>
        ))}
    </section>
  )
})
