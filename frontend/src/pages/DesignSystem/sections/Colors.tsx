import { Code, Section, Sub, Swatch, TableHead } from '../primitives'
import { readableFg } from '../lib'

const SAGE_SCALE = [
  { token: 'sage-50', hex: '#e8e7e1' },
  { token: 'sage-100', hex: '#d4cfcb' },
  { token: 'sage-200', hex: '#c9ccc8' },
  { token: 'sage-300', hex: '#b4bdaf' },
  { token: 'sage-400', hex: '#a0a89d' },
  { token: 'sage-500', hex: '#626e5c' },
  { token: 'sage-600', hex: '#3e4739' },
  { token: 'sage-700', hex: '#2b3625' },
  { token: 'sage-800', hex: '#1f271b' },
  { token: 'sage-900', hex: '#181e15' },
  { token: 'sage-950', hex: '#141a11' },
]

const SEMANTIC_ROWS = [
  { token: 'bg', alias: 'background', hex: '#e9e8e7', usage: '앱 기본 배경' },
  { token: 'surface', alias: 'sage-50', hex: '#e8e7e1', usage: '컴포넌트 배경' },
  { token: 'surface-raised', alias: 'white', hex: '#ffffff', usage: '카드 · 모달' },
  { token: 'border', alias: 'sage-100', hex: '#d4cfcb', usage: '기본 보더' },
  { token: 'border-strong', alias: 'sage-200', hex: '#c9ccc8', usage: '강조 보더' },
  { token: 'fg', alias: 'sage-950', hex: '#141a11', usage: '기본 텍스트' },
  { token: 'fg-strong', alias: 'sage-800', hex: '#1f271b', usage: '헤딩' },
  { token: 'fg-muted', alias: 'sage-400', hex: '#a0a89d', usage: '보조 텍스트' },
  { token: 'fg-subtle', alias: 'sage-300', hex: '#b4bdaf', usage: '흐린 텍스트' },
  { token: 'fg-disabled', alias: 'sage-200', hex: '#c9ccc8', usage: '비활성' },
  { token: 'primary', alias: 'sage-500', hex: '#626e5c', usage: 'Primary 액션' },
  { token: 'primary-hover', alias: 'sage-600', hex: '#3e4739', usage: 'hover' },
  { token: 'primary-pressed', alias: 'sage-700', hex: '#2b3625', usage: 'active' },
]

const STATUS_COLORS = [
  {
    key: 'success',
    name: 'Success',
    example: 'COMPLETED · ANALYZED',
    shades: [
      { label: '-50', hex: '#e8efe1' },
      { label: '-500', hex: '#5b7c47' },
      { label: '-700', hex: '#3f5731' },
    ],
    bgLight: '#e8efe1',
    textLight: '#3f5731',
    bgSolid: '#5b7c47',
  },
  {
    key: 'warning',
    name: 'Warning',
    example: 'IN_PROGRESS · ANALYZING',
    shades: [
      { label: '-50', hex: '#f4e8d4' },
      { label: '-500', hex: '#b88840' },
      { label: '-700', hex: '#8a6529' },
    ],
    bgLight: '#f4e8d4',
    textLight: '#8a6529',
    bgSolid: '#b88840',
  },
  {
    key: 'danger',
    name: 'Danger',
    example: 'FAILED · 삭제',
    shades: [
      { label: '-50', hex: '#f4e0d8' },
      { label: '-500', hex: '#a8503c' },
      { label: '-700', hex: '#803a2a' },
    ],
    bgLight: '#f4e0d8',
    textLight: '#803a2a',
    bgSolid: '#a8503c',
  },
  {
    key: 'info',
    name: 'Info',
    example: '정보 토스트 · 안내',
    shades: [
      { label: '-50', hex: '#dde4ea' },
      { label: '-500', hex: '#4d6878' },
      { label: '-700', hex: '#36475a' },
    ],
    bgLight: '#dde4ea',
    textLight: '#36475a',
    bgSolid: '#4d6878',
  },
]

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
        <div className="text-button font-semibold text-fg leading-tight">
          {name}
        </div>
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
      description="Sage 모노크로매틱을 베이스로 Status · Domain 만 muted jewel tones 으로 식별성을 부여."
    >
      <Sub title="Sage Scale">
        <div className="grid grid-cols-4 sm:grid-cols-6 lg:grid-cols-11 gap-2">
          {SAGE_SCALE.map((c) => (
            <Swatch key={c.token} token={c.token} hex={c.hex} />
          ))}
        </div>
      </Sub>

      <Sub title="Semantic Aliases">
        <div className="border border-border rounded-lg overflow-hidden bg-surface-raised">
          <table className="w-full text-button">
            <TableHead cols={['Token', 'Alias', 'Color', 'Usage']} />
            <tbody className="divide-y divide-border">
              {SEMANTIC_ROWS.map((row) => (
                <tr
                  key={row.token}
                  className="hover:bg-surface transition-colors duration-fast"
                >
                  <td className="px-4 py-2.5 font-mono text-fg text-caption">
                    {row.token}
                  </td>
                  <td className="px-4 py-2.5 text-fg-muted font-mono text-caption">
                    {row.alias}
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex items-center gap-2">
                      <span
                        className="inline-block w-5 h-5 rounded-sm border border-border shrink-0"
                        style={{ backgroundColor: row.hex }}
                      />
                      <span className="font-mono text-fg-muted text-caption">
                        {row.hex}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-2.5 text-fg-muted">{row.usage}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Sub>

      <Sub title="Status">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {STATUS_COLORS.map((s) => (
            <div
              key={s.key}
              className="border border-border rounded-lg overflow-hidden bg-surface-raised"
            >
              <div className="px-4 py-3 bg-surface border-b border-border flex items-center justify-between gap-3">
                <span className="font-subheading text-h6 font-bold text-fg-strong">
                  {s.name}
                </span>
                <span className="text-caption text-fg-muted truncate font-mono">
                  {s.example}
                </span>
              </div>
              <div className="p-4 space-y-3">
                <div className="grid grid-cols-3 gap-2">
                  {s.shades.map((sh) => (
                    <Swatch
                      key={sh.label}
                      token={`${s.key}${sh.label}`}
                      hex={sh.hex}
                    />
                  ))}
                </div>
                <div className="flex flex-wrap gap-2 pt-1">
                  <span
                    className="px-3 py-1 rounded-pill text-button font-semibold"
                    style={{ backgroundColor: s.bgLight, color: s.textLight }}
                  >
                    Light Badge
                  </span>
                  <span
                    className="px-3 py-1 rounded-pill text-button font-semibold text-white"
                    style={{ backgroundColor: s.bgSolid }}
                  >
                    Solid Badge
                  </span>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  <Code>{`bg-${s.key}-50 text-${s.key}-700`}</Code>
                  <Code>{`bg-${s.key} text-white`}</Code>
                </div>
              </div>
            </div>
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
