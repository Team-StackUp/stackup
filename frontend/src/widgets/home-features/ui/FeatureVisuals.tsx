/**
 * 기능 섹션의 제품 UI 조각들. 값은 시연용 고정값이지만 축 이름·등급·지표는 실제 스키마와 같다.
 * (채점 4축, 면접관 패널 라벨, 전달력 지표 = WPM·무음·간투어)
 */

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-border bg-surface-raised p-5 shadow-sm lg:p-6">
      {children}
    </div>
  )
}

function AxisRow({
  label,
  value,
  max = 5,
  weakest = false,
}: {
  label: string
  value: number
  max?: number
  weakest?: boolean
}) {
  return (
    <li className="flex items-center gap-3">
      <span className="w-14 shrink-0 text-caption text-fg-muted">{label}</span>
      <span className="h-1.5 flex-1 overflow-hidden rounded-pill bg-border">
        <span
          className={`block h-full rounded-pill ${weakest ? 'bg-danger' : 'bg-primary'}`}
          style={{ width: `${(value / max) * 100}%` }}
        />
      </span>
      <span className="w-7 shrink-0 text-right text-caption font-semibold text-fg-strong">
        {value.toFixed(1)}
      </span>
      {weakest ? (
        <span className="shrink-0 rounded-pill bg-danger-50 px-2 py-0.5 text-caption font-semibold text-danger-700">
          최저
        </span>
      ) : (
        <span aria-hidden className="w-[38px] shrink-0" />
      )}
    </li>
  )
}

/** 가장 낮은 축을 겨냥해 꼬리질문이 만들어지는 흐름. */
export function FollowupVisual() {
  return (
    <Card>
      <div className="text-caption font-medium text-fg-subtle">직전 답변 채점</div>
      <ul className="mt-3 space-y-2.5">
        <AxisRow label="구체성" value={1.5} weakest />
        <AxisRow label="논리" value={3.5} />
        <AxisRow label="정확성" value={4.0} />
      </ul>

      <div className="mt-5 border-t border-border pt-4">
        <span className="rounded-pill bg-primary-100 px-2 py-0.5 text-caption font-semibold text-primary-fg">
          구체성을 겨냥한 꼬리질문
        </span>
        <p className="mt-2.5 text-[14px] leading-relaxed text-fg-strong">
          “숫자로 말씀해 주세요. 개선 전후 p99 응답시간이 어떻게 바뀌었나요?”
        </p>
      </div>
    </Card>
  )
}

/** 점수에 근거와 인용이 붙는 화면. */
export function ScoringVisual() {
  return (
    <Card>
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-[14px] font-semibold text-fg-strong">기술 정확도·깊이</span>
        <span className="text-h5 font-bold text-fg">79</span>
      </div>

      <div className="mt-4 rounded-xl bg-surface px-3.5 py-3">
        <div className="text-caption font-medium text-fg-subtle">점수 근거</div>
        <p className="mt-1.5 text-caption leading-relaxed text-fg-strong">
          Q3에서 동시성 제어를 RDB 락으로만 설명해 분산 환경 고려가 빠졌습니다.
        </p>
      </div>

      <ul className="mt-3 space-y-1.5">
        {['이력서 > 결제 시스템 개선', 'pay-api > OrderService.java'].map((s) => (
          <li key={s} className="flex items-center gap-2 text-caption text-fg-muted">
            <span aria-hidden className="h-1 w-1 shrink-0 rounded-pill bg-fg-subtle" />
            <span className="truncate">{s}</span>
          </li>
        ))}
      </ul>
    </Card>
  )
}

export function DeliveryVisual() {
  const metrics = [
    { label: '말 속도', value: '142', unit: '어절/분' },
    { label: '무음', value: '4.2', unit: '초' },
    { label: '간투어', value: '8', unit: '회' },
  ]
  return (
    <div className="mt-6 rounded-xl border border-border bg-surface/70 px-3.5 py-3">
      <dl className="space-y-2">
        {metrics.map((m) => (
          <div key={m.label} className="flex items-baseline gap-2">
            <dt className="w-14 shrink-0 text-caption text-fg-muted">{m.label}</dt>
            <dd className="text-[14px] font-semibold text-fg-strong">
              {m.value}
              <span className="ml-1 text-caption font-normal text-fg-subtle">{m.unit}</span>
            </dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

export function PanelVisual() {
  const rows = [
    { label: '백엔드', v: 79 },
    { label: '논리', v: 85 },
    { label: '전달', v: 88 },
  ]
  return (
    <div className="mt-6 rounded-xl border border-border bg-surface/70 px-3.5 py-3">
      <ul className="space-y-2">
        {rows.map((r) => (
          <li key={r.label} className="flex items-center justify-between gap-2">
            <span className="text-caption text-fg-muted">{r.label} 면접관</span>
            <span className="text-[14px] font-semibold text-fg-strong">{r.v}</span>
          </li>
        ))}
      </ul>
      <div className="mt-2.5 flex items-center justify-between gap-2 border-t border-border pt-2.5">
        <span className="text-caption font-medium text-fg-strong">종합</span>
        <span className="text-[14px] font-bold text-primary-fg">82</span>
      </div>
    </div>
  )
}

export function ReportVisual() {
  return (
    <div className="mt-6 rounded-xl border border-border bg-surface/70 px-3.5 py-3">
      <div className="flex items-center gap-2 rounded-lg border border-border bg-surface-raised px-2.5 py-2">
        <span className="truncate text-caption text-fg-muted">stack-up.shop/share/a1b2c3</span>
        <span className="ml-auto shrink-0 text-caption font-semibold text-primary-fg">복사</span>
      </div>
      <div className="mt-2 flex gap-2">
        <span className="rounded-pill bg-surface px-2 py-0.5 text-caption text-fg-muted">
          PDF 저장
        </span>
        <span className="rounded-pill bg-surface px-2 py-0.5 text-caption text-fg-muted">
          링크 공유
        </span>
      </div>
    </div>
  )
}
