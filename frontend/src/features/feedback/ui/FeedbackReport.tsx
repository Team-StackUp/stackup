import { StatusBadge } from '@/shared/ui/StatusBadge'
import { ScoreBar } from '@/shared/ui/ScoreBar'
import type { Feedback } from '../api/feedbackApi'

export function FeedbackReport({ feedback }: { feedback: Feedback }) {
  const overall = feedback.overallScore
  return (
    <div className="flex w-full flex-col gap-8">
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
  )
}
