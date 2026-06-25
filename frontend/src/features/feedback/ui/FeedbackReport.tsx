import { useRef, useState } from 'react'
import { StatusBadge } from '@/shared/ui/StatusBadge'
import { ScoreBar } from '@/shared/ui/ScoreBar'
import { Button } from '@/shared/ui/Button'
import { useCopyToClipboard } from '@/shared/hooks'
import type { Feedback } from '../api/feedbackApi'
import { downloadElementAsPdf } from '../lib/downloadPdf'
import { useShareFeedback } from '../model/useFeedback'

// AI 가 별도 정성 평가를 패널 항목으로 실어 보낼 때 쓰는 라벨(모두 종합 점수엔 미포함).
const SELF_INTRO_LABEL = '첫인상'
const JOB_FIT_LABEL = '직무 적합도'
const ROLE_UNDERSTANDING_LABEL = '직무 이해도'

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
  // '첫인상'·'직무 적합도'는 종합 점수에 포함되지 않는 별도 정성 평가 → 패널과 분리해 전용 섹션으로.
  const panel = feedback.panelBreakdown ?? []
  const selfIntro = panel.find((b) => b.evaluator === SELF_INTRO_LABEL)
  const jobFit = panel.find((b) => b.evaluator === JOB_FIT_LABEL)
  const roleUnderstanding = panel.find((b) => b.evaluator === ROLE_UNDERSTANDING_LABEL)
  const SPECIAL = [SELF_INTRO_LABEL, JOB_FIT_LABEL, ROLE_UNDERSTANDING_LABEL]
  const interviewerPanel = panel.filter((b) => !SPECIAL.includes(b.evaluator ?? ''))
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

        {jobFit && (
          <section className="flex flex-col gap-2">
            <h2 className="text-h6 text-fg">직무 적합도</h2>
            <p className="text-caption text-fg-subtle">
              채용공고(JD) 요구 대비 적합도·갭 평가입니다. 종합 점수에는 반영되지 않습니다.
            </p>
            <div className="flex flex-col gap-1.5">
              <ScoreBar label="직무 적합도" score={jobFit.score} />
              {jobFit.detail && (
                <p className="whitespace-pre-wrap pl-1 text-caption text-fg-muted">
                  {jobFit.detail}
                </p>
              )}
              {(jobFit.strength || jobFit.weakness) && (
                <div className="flex flex-col gap-0.5 pl-1 text-caption text-fg-muted">
                  {jobFit.strength && <span>충족 · {jobFit.strength}</span>}
                  {jobFit.weakness && <span>갭 · {jobFit.weakness}</span>}
                </div>
              )}
              {jobFit.scoreRationale && (
                <p className="pl-1 text-caption text-fg-subtle">
                  점수 근거 · {jobFit.scoreRationale}
                </p>
              )}
            </div>
          </section>
        )}

        {roleUnderstanding && (
          <section className="flex flex-col gap-2">
            <h2 className="text-h6 text-fg">직무 이해도</h2>
            <p className="text-caption text-fg-subtle">
              직무가 무엇을 하는 자리인지에 대한 이해·지원동기 평가입니다. 종합 점수에는 반영되지
              않습니다.
            </p>
            <div className="flex flex-col gap-1.5">
              <ScoreBar label="직무 이해도" score={roleUnderstanding.score} />
              {roleUnderstanding.detail && (
                <p className="whitespace-pre-wrap pl-1 text-caption text-fg-muted">
                  {roleUnderstanding.detail}
                </p>
              )}
              {(roleUnderstanding.strength || roleUnderstanding.weakness) && (
                <div className="flex flex-col gap-0.5 pl-1 text-caption text-fg-muted">
                  {roleUnderstanding.strength && <span>강점 · {roleUnderstanding.strength}</span>}
                  {roleUnderstanding.weakness && <span>보완 · {roleUnderstanding.weakness}</span>}
                </div>
              )}
              {roleUnderstanding.scoreRationale && (
                <p className="pl-1 text-caption text-fg-subtle">
                  점수 근거 · {roleUnderstanding.scoreRationale}
                </p>
              )}
            </div>
          </section>
        )}

        {selfIntro && (
          <section className="flex flex-col gap-2">
            <h2 className="text-h6 text-fg">자기소개 첫인상</h2>
            <p className="text-caption text-fg-subtle">
              전달력·구성·직무적합성 평가입니다. 종합 점수에는 반영되지 않습니다.
            </p>
            <div className="flex flex-col gap-1.5">
              <ScoreBar label="첫인상" score={selfIntro.score} />
              {selfIntro.detail && (
                <p className="whitespace-pre-wrap pl-1 text-caption text-fg-muted">
                  {selfIntro.detail}
                </p>
              )}
              {(selfIntro.strength || selfIntro.weakness) && (
                <div className="flex flex-col gap-0.5 pl-1 text-caption text-fg-muted">
                  {selfIntro.strength && <span>강점 · {selfIntro.strength}</span>}
                  {selfIntro.weakness && <span>보완 · {selfIntro.weakness}</span>}
                </div>
              )}
              {selfIntro.scoreRationale && (
                <p className="pl-1 text-caption text-fg-subtle">
                  점수 근거 · {selfIntro.scoreRationale}
                </p>
              )}
            </div>
          </section>
        )}

        {interviewerPanel.length > 0 && (
          <section className="flex flex-col gap-3">
            <h2 className="text-h6 text-fg">면접관 패널 평가</h2>
            <div className="flex flex-col gap-4">
              {interviewerPanel.map((b) => (
                <div key={b.evaluator} className="flex flex-col gap-1.5">
                  <ScoreBar label={`${b.evaluator} 면접관`} score={b.score} />
                  {b.detail && (
                    <p className="whitespace-pre-wrap pl-1 text-caption text-fg-muted">
                      {b.detail}
                    </p>
                  )}
                  {(b.strength || b.weakness) && (
                    <div className="flex flex-col gap-0.5 pl-1 text-caption text-fg-muted">
                      {b.strength && <span>강점 · {b.strength}</span>}
                      {b.weakness && <span>보완 · {b.weakness}</span>}
                    </div>
                  )}
                  {b.scoreRationale && (
                    <p className="pl-1 text-caption text-fg-subtle">점수 근거 · {b.scoreRationale}</p>
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

        {feedback.studyPlan && feedback.studyPlan.length > 0 && (
          <section className="flex flex-col gap-2">
            <h2 className="text-h6 text-fg">학습 방향</h2>
            <ul className="flex flex-col gap-1.5">
              {feedback.studyPlan.map((step, i) => (
                <li key={i} className="flex gap-2 text-body text-fg-muted">
                  <span aria-hidden className="text-primary">›</span>
                  <span className="whitespace-pre-wrap">{step}</span>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </div>
  )
}
