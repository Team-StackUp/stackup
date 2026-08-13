import { Code, Section, Sub, Swatch, TableHead } from '../primitives'
import { readableFg } from '../lib'

/**
 * 컬러는 SEED 토큰을 참조하므로 라이트/다크에서 값이 달라진다.
 * 그래서 시맨틱·Status 는 hex 를 적지 않고 실제 유틸리티 클래스로 렌더한다 —
 * 이 페이지를 다크모드로 보면 그대로 다크 값이 보이는 게 맞다.
 * 고정값인 것(고정 톤 스케일, Domain)만 hex 를 표기한다.
 */

const FIXED_SCALE = [
  { token: 'sage-50', hex: '#eef1f6' },
  { token: 'sage-100', hex: '#dbe2ec' },
  { token: 'sage-200', hex: '#c6cedd' },
  { token: 'sage-300', hex: '#9fafc9' },
  { token: 'sage-400', hex: '#6e7f9f' },
  { token: 'sage-500', hex: '#4a5a7e' },
  { token: 'sage-600', hex: '#38486a' },
  { token: 'sage-700', hex: '#2b3a59' },
  { token: 'sage-800', hex: '#1e2a44' },
  { token: 'sage-900', hex: '#161f33' },
  { token: 'sage-950', hex: '#101627' },
]

const SEMANTIC_ROWS: {
  token: string
  source: string
  swatch: string
  usage: string
}[] = [
  { token: 'bg', source: 'bg-layer-basement', swatch: 'bg-bg', usage: '앱 기본 배경' },
  { token: 'surface', source: 'bg-layer-fill', swatch: 'bg-surface', usage: '연한 구획' },
  {
    token: 'surface-raised',
    source: 'bg-layer-default',
    swatch: 'bg-surface-raised',
    usage: '카드',
  },
  {
    token: 'surface-floating',
    source: 'bg-layer-floating',
    swatch: 'bg-surface-floating',
    usage: '모달 · 팝오버',
  },
  {
    token: 'border',
    source: 'stroke-neutral-subtle',
    swatch: 'bg-border',
    usage: '기본 보더(헤어라인)',
  },
  {
    token: 'border-strong',
    source: 'stroke-neutral-muted',
    swatch: 'bg-border-strong',
    usage: '강조 보더',
  },
  { token: 'fg', source: 'fg-neutral', swatch: 'bg-fg', usage: '기본 텍스트' },
  {
    token: 'fg-strong',
    source: 'fg-neutral-muted',
    swatch: 'bg-fg-strong',
    usage: '헤딩',
  },
  {
    token: 'fg-muted',
    source: 'fg-neutral-subtle',
    swatch: 'bg-fg-muted',
    usage: '보조 텍스트',
  },
  {
    token: 'fg-subtle',
    source: 'fg-placeholder',
    swatch: 'bg-fg-subtle',
    usage: '흐린 텍스트',
  },
  { token: 'fg-disabled', source: 'fg-disabled', swatch: 'bg-fg-disabled', usage: '비활성' },
  {
    token: 'fg-on-primary',
    source: 'palette-static-white',
    swatch: 'bg-fg-on-primary',
    usage: 'brand solid 위 텍스트',
  },
  {
    token: 'primary',
    source: 'bg-brand-solid',
    swatch: 'bg-primary',
    usage: '브랜드 배경(버튼·보더)',
  },
  {
    token: 'primary-hover',
    source: 'bg-brand-solid-pressed',
    swatch: 'bg-primary-hover',
    usage: 'hover · active',
  },
  {
    token: 'primary-fg',
    source: 'fg-brand',
    swatch: 'bg-primary-fg',
    usage: '브랜드 텍스트',
  },
  { token: 'primary-50', source: 'bg-brand-weak', swatch: 'bg-primary-50', usage: '연한 틴트' },
  {
    token: 'primary-100',
    source: 'bg-brand-weak-pressed',
    swatch: 'bg-primary-100',
    usage: '틴트 강조',
  },
  {
    token: 'primary-200',
    source: 'stroke-brand-weak',
    swatch: 'bg-primary-200',
    usage: '틴트 위 보더',
  },
]

/**
 * 클래스는 반드시 리터럴로 적는다 — Tailwind 는 소스를 정적 스캔하므로
 * `bg-${key}-50` 처럼 조립하면 유틸리티가 생성되지 않는다.
 */
const STATUS_COLORS = [
  {
    key: 'success',
    name: 'Success',
    example: 'COMPLETED · ANALYZED',
    seed: 'positive',
    weak: 'bg-success-50 text-success-700',
    solid: 'bg-success text-fg-on-primary',
  },
  {
    key: 'warning',
    name: 'Warning',
    example: 'IN_PROGRESS · ANALYZING',
    seed: 'warning',
    weak: 'bg-warning-50 text-warning-700',
    solid: 'bg-warning text-fg-on-primary',
  },
  {
    key: 'danger',
    name: 'Danger',
    example: 'FAILED · 삭제',
    seed: 'critical',
    weak: 'bg-danger-50 text-danger-700',
    solid: 'bg-danger text-fg-on-primary',
  },
  {
    key: 'info',
    name: 'Info',
    example: '정보 토스트 · 안내',
    seed: 'informative',
    weak: 'bg-info-50 text-info-700',
    solid: 'bg-info text-fg-on-primary',
  },
] as const

const DOMAIN_JOBS = [
  { token: 'job-frontend', hex: '#5e8a98', name: 'Frontend', tone: 'teal' },
  { token: 'job-backend', hex: '#7d6c93', name: 'Backend', tone: 'plum' },
  { token: 'job-infra', hex: '#b06c70', name: 'Infra', tone: 'rose' },
  { token: 'job-dba', hex: '#b89c5e', name: 'DBA', tone: 'gold' },
]

const DOMAIN_TYPES = [
  { token: 'type-personality', hex: '#6f9978', name: 'Personality', tone: 'mint' },
  { token: 'type-technical', hex: '#6c8294', name: 'Technical', tone: 'slate' },
  { token: 'type-live-coding', hex: '#a87385', name: 'Live Coding', tone: 'mauve' },
  { token: 'type-integrated', hex: '#8a7896', name: 'Integrated', tone: 'violet' },
]

function DomainCard({
  token,
  hex,
  name,
  tone,
}: {
  token: string
  hex: string
  name: string
  tone: string
}) {
  return (
    <div className="rounded-md overflow-hidden border border-border bg-surface-raised hover:border-border-strong transition-colors duration-fast">
      <div
        className="h-16 flex flex-col justify-end p-2 font-mono text-[10px]"
        style={{ backgroundColor: hex, color: readableFg(hex) }}
      >
        {hex.toUpperCase()}
      </div>
      <div className="px-3 py-2.5">
        <div className="text-button font-semibold text-fg leading-tight">{name}</div>
        <div className="text-[10px] text-fg-muted font-mono mt-0.5 truncate">
          {token} · {tone}
        </div>
      </div>
    </div>
  )
}

export function ColorsSection() {
  return (
    <Section
      id="colors"
      label="01 COLORS"
      title="컬러 시스템"
      description="컬러·radius·shadow 는 당근 SEED Design 토큰을 참조한다(라이트/다크 자동 전환). 고정 톤 스케일과 Domain 만 자체 값을 유지."
    >
      <Sub title="Semantic Aliases (SEED 참조)">
        <div className="border border-border rounded-lg overflow-hidden bg-surface-raised">
          <table className="w-full text-button">
            <TableHead cols={['Token', 'SEED 출처', 'Color', 'Usage']} />
            <tbody className="divide-y divide-border">
              {SEMANTIC_ROWS.map((row) => (
                <tr
                  key={row.token}
                  className="hover:bg-surface transition-colors duration-fast"
                >
                  <td className="px-4 py-2.5 font-mono text-fg text-caption">{row.token}</td>
                  <td className="px-4 py-2.5 text-fg-muted font-mono text-caption">
                    {row.source}
                  </td>
                  <td className="px-4 py-2.5">
                    <span
                      className={`inline-block w-6 h-6 rounded-sm border border-border shrink-0 ${row.swatch}`}
                    />
                  </td>
                  <td className="px-4 py-2.5 text-fg-muted">{row.usage}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Sub>

      <Sub title="Status (SEED 참조)">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {STATUS_COLORS.map((s) => (
            <div
              key={s.key}
              className="border border-border rounded-lg overflow-hidden bg-surface-raised"
            >
              <div className="px-4 py-3 bg-surface border-b border-border flex items-center justify-between gap-3">
                <span className="font-sans text-[18px] font-bold tracking-[-0.02em] text-fg">
                  {s.name}
                </span>
                <span className="text-caption text-fg-muted truncate font-mono">
                  {s.example}
                </span>
              </div>
              <div className="p-4 space-y-3">
                <div className="text-caption text-fg-muted font-mono">
                  SEED · {s.seed}
                </div>
                <div className="flex flex-wrap gap-2 pt-1">
                  <span className={`px-3 py-1 rounded-pill text-button font-semibold ${s.weak}`}>
                    Light Badge
                  </span>
                  <span className={`px-3 py-1 rounded-pill text-button font-semibold ${s.solid}`}>
                    Solid Badge
                  </span>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  <Code>{s.weak}</Code>
                  <Code>{s.solid}</Code>
                </div>
              </div>
            </div>
          ))}
        </div>
      </Sub>

      <Sub title="고정 톤 스케일 (mode 무관)">
        <p className="mb-3 text-caption text-fg-muted">
          항상 어두워야 하는 표면(푸터·다크 패널)에만 쓴다. 표면·본문 의미로 쓰면 다크모드에서
          반전되지 않아 깨진다.
        </p>
        <div className="grid grid-cols-4 sm:grid-cols-6 lg:grid-cols-11 gap-2">
          {FIXED_SCALE.map((c) => (
            <Swatch key={c.token} token={c.token} hex={c.hex} />
          ))}
        </div>
      </Sub>

      <Sub title="Domain">
        <div className="space-y-6">
          <div>
            <div className="text-caption text-fg-muted uppercase tracking-wider mb-3 font-semibold">
              직군 (Jobs)
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {DOMAIN_JOBS.map((d) => (
                <DomainCard key={d.token} {...d} />
              ))}
            </div>
          </div>
          <div>
            <div className="text-caption text-fg-muted uppercase tracking-wider mb-3 font-semibold">
              면접 유형 (Interview Types)
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {DOMAIN_TYPES.map((d) => (
                <DomainCard key={d.token} {...d} />
              ))}
            </div>
          </div>
        </div>
      </Sub>
    </Section>
  )
}
