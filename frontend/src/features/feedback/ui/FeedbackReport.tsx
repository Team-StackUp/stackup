import { useMemo, useRef, useState } from 'react'
import { StatusBadge } from '@/shared/ui/StatusBadge'
import { ScoreBar } from '@/shared/ui/ScoreBar'
import { Button } from '@/shared/ui/Button'
import { Eyebrow, Panel, toast } from '@/shared/ui'
import { useCopyToClipboard } from '@/shared/hooks'
import type { Feedback } from '../api/feedbackApi'
import { downloadElementAsPdf } from '../lib/downloadPdf'
import { useShareFeedback, useUnshareFeedback } from '../model/useFeedback'
import { HighlightedText } from './HighlightedText'

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
    } catch {
      toast.error('PDF 생성에 실패했어요. 다시 시도해 주세요.')
    } finally {
      setDownloading(false)
    }
  }

  const share = useShareFeedback(feedback.sessionId ?? 0)
  const unshare = useUnshareFeedback(feedback.sessionId ?? 0)
  const { copy, copied } = useCopyToClipboard()
  // 이미 공유된 상태(shareToken 보유)면 발급 없이 링크만 복사한다.
  const isShared = Boolean(feedback.shareToken)
  const handleShare = async () => {
    // 실패 토스트는 useShareFeedback.onError 가 띄운다 — 여기선 unhandled rejection 만 방지.
    try {
      const token = feedback.shareToken ?? (await share.mutateAsync())
      if (token) await copy(`${window.location.origin}/share/${token}`)
    } catch {
      /* handled by mutation onError */
    }
  }

  const overall = feedback.overallScore
  // 강조 대상: AI 가 고른 핵심 구절 ∪ 다음에 채울 키워드. 본문 문단에서 <mark> 처리.
  // 참조를 고정하지 않으면 HighlightedText 8곳의 useMemo([terms])가 매 렌더 미스한다.
  const highlightTerms = useMemo(
    () => [...(feedback.highlights ?? []), ...(feedback.improvementKeywords ?? [])],
    [feedback.highlights, feedback.improvementKeywords],
  )
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
            {copied
              ? '링크 복사됨!'
              : share.isPending
                ? '공유 준비 중…'
                : isShared
                  ? '공유 링크 복사'
                  : '공유'}
          </Button>
        )}
        {shareable && isShared && (
          <Button
            variant="secondary"
            onClick={() => unshare.mutate()}
            disabled={unshare.isPending}
          >
            {unshare.isPending ? '해제 중…' : '공유 해제'}
          </Button>
        )}
        <Button variant="secondary" onClick={handleDownload} disabled={downloading}>
          {downloading ? 'PDF 생성 중…' : 'PDF 다운로드'}
        </Button>
      </div>

      <div ref={reportRef} className="flex w-full flex-col gap-10 bg-surface-raised p-2">
        <section className="flex flex-col gap-6 border-b border-border pb-8 sm:flex-row sm:items-end sm:gap-10">
          <div>
            <Eyebrow as="h2">종합 점수</Eyebrow>
            <p className="mt-2 flex items-baseline gap-1.5">
              <span
                className="font-sans font-bold leading-none text-fg"
                style={{ fontSize: 'clamp(56px, 8vw, 88px)', letterSpacing: '-0.05em' }}
              >
                {typeof overall === 'number' ? Math.round(overall) : '—'}
              </span>
              <span className="text-rich font-normal text-fg-subtle">/ 100</span>
            </p>
          </div>

          <div className="flex flex-1 flex-col gap-4 sm:pb-2">
            <ScoreBar label="기술 정확도" score={feedback.technicalAccuracy} />
            <ScoreBar label="논리력" score={feedback.logicScore} />
            <ScoreBar label="전달력" score={feedback.communicationScore} />
          </div>
        </section>

        {/* 핵심 서사: 강점 → 개선 → 키워드 → 학습방향 (점수 직후로 끌어올려 스캔성↑) */}
        {feedback.strengthsSummary && (
          <section className="flex flex-col gap-2">
            <Eyebrow as="h2">강점</Eyebrow>
            <p className="whitespace-pre-wrap text-body font-normal leading-relaxed text-fg-strong">
              <HighlightedText text={feedback.strengthsSummary} terms={highlightTerms} />
            </p>
          </section>
        )}

        {feedback.weaknessesSummary && (
          <section className="flex flex-col gap-2">
            <Eyebrow as="h2">개선할 점</Eyebrow>
            <p className="whitespace-pre-wrap text-body font-normal leading-relaxed text-fg-strong">
              <HighlightedText text={feedback.weaknessesSummary} terms={highlightTerms} />
            </p>
          </section>
        )}

        {feedback.improvementKeywords && feedback.improvementKeywords.length > 0 && (
          <section className="flex flex-col gap-2">
            <Eyebrow as="h2">다음에 채울 키워드</Eyebrow>
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
            <Eyebrow as="h2">학습 방향</Eyebrow>
            <ul className="flex flex-col gap-1.5">
              {feedback.studyPlan.map((step, i) => (
                <li key={i} className="flex gap-2 text-body font-normal leading-relaxed text-fg-strong">
                  <span aria-hidden className="text-primary-fg">›</span>
                  <span className="whitespace-pre-wrap">
                    <HighlightedText text={step} terms={highlightTerms} />
                  </span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {interviewerPanel.length > 0 && (
          <section className="flex flex-col gap-3">
            <Eyebrow as="h2">면접관 패널 평가</Eyebrow>
            <div className="flex flex-col gap-4">
              {interviewerPanel.map((b) => (
                <Panel key={b.evaluator} padding="sm" className="flex flex-col gap-1.5">
                  <ScoreBar label={`${b.evaluator} 면접관`} score={b.score} />
                  {b.detail && (
                    <p className="whitespace-pre-wrap pl-1 text-caption text-fg-muted">
                      <HighlightedText text={b.detail} terms={highlightTerms} />
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
                </Panel>
              ))}
            </div>
          </section>
        )}

        {/* 추가 평가 — 종합 점수에 미반영. 면책은 그룹 헤더에서 한 번만. */}
        {(jobFit || roleUnderstanding || selfIntro) && (
          <section className="flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <Eyebrow as="h2">추가 평가</Eyebrow>
              <p className="text-caption text-fg-subtle">
                아래 항목은 종합 점수에 반영되지 않는 참고용 평가입니다.
              </p>
            </div>

            {jobFit && (
              <div className="flex flex-col gap-1.5">
                <h3 className="text-body font-bold tracking-[-0.02em] text-fg">직무 적합도</h3>
                <p className="pl-1 text-caption text-fg-subtle">채용공고(JD) 요구 대비 적합도·갭</p>
                <ScoreBar label="직무 적합도" score={jobFit.score} />
                {jobFit.detail && (
                  <p className="whitespace-pre-wrap pl-1 text-caption text-fg-muted">
                    <HighlightedText text={jobFit.detail} terms={highlightTerms} />
                  </p>
                )}
                {(jobFit.strength || jobFit.weakness) && (
                  <div className="flex flex-col gap-0.5 pl-1 text-caption text-fg-muted">
                    {jobFit.strength && <span>충족 · {jobFit.strength}</span>}
                    {jobFit.weakness && <span>갭 · {jobFit.weakness}</span>}
                  </div>
                )}
                {jobFit.scoreRationale && (
                  <p className="pl-1 text-caption text-fg-subtle">점수 근거 · {jobFit.scoreRationale}</p>
                )}
              </div>
            )}

            {roleUnderstanding && (
              <div className="flex flex-col gap-1.5">
                <h3 className="text-body font-bold tracking-[-0.02em] text-fg">직무 이해도</h3>
                <p className="pl-1 text-caption text-fg-subtle">직무에 대한 이해·지원동기</p>
                <ScoreBar label="직무 이해도" score={roleUnderstanding.score} />
                {roleUnderstanding.detail && (
                  <p className="whitespace-pre-wrap pl-1 text-caption text-fg-muted">
                    <HighlightedText text={roleUnderstanding.detail} terms={highlightTerms} />
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
            )}

            {selfIntro && (
              <div className="flex flex-col gap-1.5">
                <h3 className="text-body font-bold tracking-[-0.02em] text-fg">자기소개 첫인상</h3>
                <p className="pl-1 text-caption text-fg-subtle">전달력·구성·직무적합성</p>
                <ScoreBar label="첫인상" score={selfIntro.score} />
                {selfIntro.detail && (
                  <p className="whitespace-pre-wrap pl-1 text-caption text-fg-muted">
                    <HighlightedText text={selfIntro.detail} terms={highlightTerms} />
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
            )}
          </section>
        )}
      </div>
    </div>
  )
}
