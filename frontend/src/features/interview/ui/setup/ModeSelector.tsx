import { RadioCardGroup } from '@/shared/ui/RadioCardGroup'
import type { SessionMode } from '@/domain/session'

const OPTIONS = [
  { value: 'TECHNICAL' as const, label: '기술 면접', description: '실무 기술·CS 위주' },
  { value: 'PERSONALITY' as const, label: '인성 면접', description: '경험·태도·협업' },
  { value: 'INTEGRATED' as const, label: '종합 면접', description: '기술 + 인성 혼합' },
  {
    value: 'JOB_TAILORED' as const,
    label: '직무 맞춤 면접',
    description: '채용공고(JD) 기반 적합도·지원동기 (JD 입력 필요)',
  },
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
