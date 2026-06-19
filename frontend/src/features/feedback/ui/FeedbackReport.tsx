import { useRef, useState } from 'react'
import { StatusBadge } from '@/shared/ui/StatusBadge'
import { ScoreBar } from '@/shared/ui/ScoreBar'
import { Button } from '@/shared/ui/Button'
import { useCopyToClipboard } from '@/shared/hooks'
import type { Feedback } from '../api/feedbackApi'
import { downloadElementAsPdf } from '../lib/downloadPdf'
import { useShareFeedback } from '../model/useFeedback'

// shareable: 소유자 화면에서만 '공유' 버튼 노출(공개 페이지에선 false).
export function FeedbackReport({
  feedback,
  shareable = false,
}: {
  feedback: Feedback
  shareable?: boolean
}) {
  const reportRef = useRef<HTMLDivElement>(null)
  const [downloading, setDownloading] = useState(false)

  const handleDownload = async () => {
    if (!reportRef.current) return
    setDownloading(true)
    try {
      await downloadElementAsPdf(reportRef.current, '면접피드백.pdf')
    } finally {
      setDownloading(false)
    }
  }

  const share = useShareFeedback(feedback.sessionId ?? 0)
  const { copy, copied } = useCopyToClipboard()
  const handleShare = async () => {
    const token = await share.mutateAsync()
    if (token) await copy(`${window.location.origin}/share/${token}`)
  }

  const overall = feedback.overallScore
  return (
    <div className="flex w-full flex-col gap-4">
      <div className="flex justify-end gap-2">
        {shareable && (
          <Button
            variant="secondary"
            onClick={handleShare}
            disabled={share.isPending}
          >
            {copied ? '링크 복사됨!' : share.isPending ? '공유 준비 중…' : '공유'}
          </Button>
        )}
        <Button variant="secondary" onClick={handleDownload} disabled={downloading}>
          {downloading ? 'PDF 생성 중…' : 'PDF 다운로드'}
        </Button>
      </div>

      <div ref={reportRef} className="flex w-full flex-col gap-8 bg-bg p-2">
        <section className="flex flex-col items-center gap-2">
          <span className="text-caption text-fg-muted">종합 점수</span>
          <span className="text-h2 text-fg">
            {typeof overall === 'number' ? Math.round(overall) : '—'}
            <span className="text-h5 text-fg-muted"> / 100</span>
          </span>
        </section>

        <section className="flex flex-col gap-4">
          <ScoreBar label="기술 정확도" score={feedback.technicalAccuracy} />
          <ScoreBar label="논리력" score={feedback.logicScore} />
          <ScoreBar label="전달력" score={feedback.communicationScore} />
        </section>

        {feedback.panelBreakdown && feedback.panelBreakdown.length > 0 && (
          <section className="flex flex-col gap-3">
            <h2 className="text-h6 text-fg">면접관 패널 평가</h2>
            <div className="flex flex-col gap-4">
              {feedback.panelBreakdown.map((b) => (
                <div key={b.evaluator} className="flex flex-col gap-1.5">
                  <ScoreBar label={`${b.evaluator} 면접관`} score={b.score} />
                  {(b.strength || b.weakness) && (
                    <div className="flex flex-col gap-0.5 pl-1 text-caption text-fg-muted">
                      {b.strength && <span>강점 · {b.strength}</span>}
                      {b.weakness && <span>보완 · {b.weakness}</span>}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}

        {feedback.strengthsSummary && (
          <section className="flex flex-col gap-2">
            <h2 className="text-h6 text-fg">강점</h2>
            <p className="whitespace-pre-wrap text-body text-fg-muted">
              {feedback.strengthsSummary}
            </p>
          </section>
        )}

        {feedback.weaknessesSummary && (
          <section className="flex flex-col gap-2">
            <h2 className="text-h6 text-fg">개선할 점</h2>
            <p className="whitespace-pre-wrap text-body text-fg-muted">
              {feedback.weaknessesSummary}
            </p>
          </section>
        )}

        {feedback.improvementKeywords && feedback.improvementKeywords.length > 0 && (
          <section className="flex flex-col gap-2">
            <h2 className="text-h6 text-fg">다음에 채울 키워드</h2>
            <div className="flex flex-wrap gap-2">
              {feedback.improvementKeywords.map((kw) => (
                <StatusBadge key={kw} tone="info">
                  {kw}
                </StatusBadge>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  )
}
