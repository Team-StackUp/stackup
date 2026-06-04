import { CheckboxCardGroup } from '@/shared/ui/CheckboxCardGroup'
import type { JobCategory } from '@/domain/session'

const OPTIONS = [
  { value: 'FRONTEND' as const, label: '프론트엔드' },
  { value: 'BACKEND' as const, label: '백엔드' },
  { value: 'INFRA' as const, label: '인프라' },
  { value: 'DBA' as const, label: 'DBA' },
]

export function JobCategorySelector({
  value,
  onToggle,
}: {
  value: JobCategory[]
  onToggle: (value: JobCategory) => void
}) {
  return <CheckboxCardGroup ariaLabel="직군" options={OPTIONS} value={value} onToggle={onToggle} />
}
