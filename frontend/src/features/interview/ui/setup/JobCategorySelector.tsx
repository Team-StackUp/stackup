import { RadioCardGroup } from '@/shared/ui/RadioCardGroup'
import type { JobCategory } from '@/domain/session'

const OPTIONS = [
  { value: 'FRONTEND' as const, label: '프론트엔드' },
  { value: 'BACKEND' as const, label: '백엔드' },
  { value: 'INFRA' as const, label: '인프라' },
  { value: 'DBA' as const, label: 'DBA' },
]

export function JobCategorySelector({
  value,
  onChange,
}: {
  value: JobCategory | null
  onChange: (value: JobCategory) => void
}) {
  return <RadioCardGroup ariaLabel="직군" options={OPTIONS} value={value} onChange={onChange} />
}
