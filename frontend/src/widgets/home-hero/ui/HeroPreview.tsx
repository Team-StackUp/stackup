/**
 * 히어로의 제품 미리보기 — 실제 면접 화면의 한 장면을 그대로 재현한다.
 *
 * 스톡 사진 대신 제품 UI 를 보여주는 이유: 랜딩에서 증명해야 하는 건 "분위기"가 아니라
 * "답변을 파고드는 꼬리질문 + 근거 있는 4축 채점"이라는 동작이다.
 * 데이터는 시연용 고정값(제품 문구·채점 축은 실제 스키마와 동일).
 */

const SCORES = [
  { label: '구체성', value: '3.5' },
  { label: '논리', value: '4.0' },
  { label: '구조', value: '부분 STAR' },
  { label: '정확성', value: '4.5' },
]

export function HeroPreview() {
  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-surface-raised shadow-lg">
      <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3 sm:px-5">
        <div className="flex items-center gap-2 text-caption">
          <span className="relative flex h-1.5 w-1.5" aria-hidden>
            <span className="absolute inline-flex h-full w-full rounded-pill bg-success opacity-70" />
          </span>
          <span className="font-medium text-fg-strong">면접 진행 중</span>
          <span className="text-fg-subtle">· 4번째 질문</span>
        </div>
        <span className="rounded-pill bg-surface px-2.5 py-1 text-caption font-medium text-fg-muted">
          프로젝트 심층
        </span>
      </div>

      <div className="space-y-4 px-4 py-5 sm:px-5">
        <div>
          <div className="mb-1.5 text-caption font-medium text-fg-subtle">면접관</div>
          <div className="rounded-2xl rounded-tl-md bg-surface px-4 py-3">
            <p className="text-[14px] leading-relaxed text-fg-strong">
              결제 처리에 outbox 패턴을 도입했다고 쓰셨는데, 메시지 발행과 DB 커밋의 원자성은
              어떻게 보장했나요?
            </p>
            <p className="mt-2 text-caption text-fg-subtle">근거 · 이력서 &gt; 결제 시스템 개선</p>
          </div>
        </div>

        <div className="flex flex-col items-end">
          <div className="mb-1.5 text-caption font-medium text-fg-subtle">내 답변</div>
          <div className="max-w-[85%] rounded-2xl rounded-tr-md bg-primary px-4 py-3">
            <p className="text-[14px] leading-relaxed text-white">
              트랜잭션 안에서 outbox 테이블에 같이 저장하고, 별도 워커가 폴링해서 발행했습니다.
            </p>
          </div>
        </div>

        <div>
          <div className="mb-1.5 flex items-center gap-2">
            <span className="text-caption font-medium text-fg-subtle">면접관</span>
            <span className="rounded-pill bg-primary-100 px-2 py-0.5 text-caption font-semibold text-primary-pressed">
              꼬리질문
            </span>
          </div>
          <div className="rounded-2xl rounded-tl-md border border-primary-200 bg-primary-50 px-4 py-3">
            <p className="text-[14px] leading-relaxed text-fg-strong">
              폴링 주기 사이에 중복 발행이 생길 수 있는데, 컨슈머 쪽 멱등성은 어떻게 처리하셨나요?
            </p>
          </div>
        </div>

        <div className="rounded-xl border border-border bg-surface/60 px-4 py-3">
          <div className="text-caption font-medium text-fg-subtle">직전 답변 채점</div>
          <dl className="mt-2 flex flex-wrap gap-x-5 gap-y-2">
            {SCORES.map((s) => (
              <div key={s.label} className="flex items-baseline gap-1.5">
                <dt className="text-caption text-fg-muted">{s.label}</dt>
                <dd className="text-[14px] font-semibold text-fg-strong">{s.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </div>
  )
}
