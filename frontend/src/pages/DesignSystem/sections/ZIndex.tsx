import { Section, TableHead } from '../primitives'

const Z_INDEX = [
  { token: '--z-toast', value: 1600, usage: 'Toast' },
  { token: '--z-tooltip', value: 1500, usage: 'Tooltip' },
  { token: '--z-popover', value: 1400, usage: 'Popover' },
  { token: '--z-modal', value: 1300, usage: '모달 본체' },
  { token: '--z-modal-backdrop', value: 1200, usage: '모달 dim' },
  { token: '--z-sticky', value: 1100, usage: 'Sticky TopNav' },
  { token: '--z-dropdown', value: 1000, usage: 'Select · Menu' },
  { token: '--z-raised', value: 10, usage: '카드 hover' },
  { token: '--z-base', value: 0, usage: '기본 레이어' },
]

export function ZIndexSection() {
  return (
    <Section
      id="z-index"
      label="07 Z-INDEX"
      title="Z-Index"
      description="레이어 충돌 방지용 9 단계. Tailwind 자동 namespace 가 아니므로 CSS var 직접 사용 — z-[var(--z-modal)] 또는 style={{ zIndex: 'var(--z-modal)' }}"
    >
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        <div className="border border-border rounded-lg overflow-hidden bg-surface-raised">
          <table className="w-full text-button">
            <TableHead cols={['Token', 'Value', 'Usage']} />
            <tbody className="divide-y divide-border">
              {Z_INDEX.map((z) => (
                <tr
                  key={z.token}
                  className="hover:bg-surface transition-colors duration-fast"
                >
                  <td className="px-4 py-2.5 font-mono text-caption text-fg">
                    {z.token}
                  </td>
                  <td className="px-4 py-2.5 font-mono text-fg-strong text-right">
                    {z.value}
                  </td>
                  <td className="px-4 py-2.5 text-fg-muted">{z.usage}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="bg-surface border border-border p-4 rounded-lg">
          <div className="text-caption text-fg-muted uppercase tracking-wider mb-3 font-semibold">
            Stacking Order
          </div>
          <div className="space-y-1">
            {[...Z_INDEX].reverse().map((z, i) => {
              const intensity = 0.07 + i * 0.085
              return (
                <div
                  key={z.token}
                  className="flex items-center gap-3 px-3 py-1.5 rounded-sm"
                  style={{
                    backgroundColor: `rgba(98,110,92,${intensity})`,
                    color: intensity > 0.5 ? '#ffffff' : '#1e2a44',
                  }}
                >
                  <span className="font-mono text-[11px] w-10 text-right shrink-0">
                    {z.value}
                  </span>
                  <span className="text-caption truncate">{z.usage}</span>
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </Section>
  )
}
