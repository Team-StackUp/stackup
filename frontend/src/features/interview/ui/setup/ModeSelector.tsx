import { RadioCardGroup } from '@/shared/ui/RadioCardGroup'
import type { SessionMode } from '@/domain/session'

const OPTIONS = [
  { value: 'TECHNICAL' as const, label: '기술 면접' },
  { value: 'PERSONALITY' as const, label: '인성 면접' },
  { value: 'INTEGRATED' as const, label: '종합 면접' },
]

export function ModeSelector({
  value,
  onChange,
}: {
  value: SessionMode | null
  onChange: (value: SessionMode) => void
}) {
  return <RadioCardGroup ariaLabel="면접 모드" options={OPTIONS} value={value} onChange={onChange} />
}
