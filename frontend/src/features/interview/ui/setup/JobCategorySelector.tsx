import {
  ENGINEERING_JOB_CATEGORIES,
  NON_ENGINEERING_JOB_CATEGORIES,
} from '@/domain/session/model/jobCategory'
import { CheckboxCardGroup } from '@/shared/ui/CheckboxCardGroup'
import type { JobCategory } from '@/domain/session'

/**
 * 직군 선택.
 *
 * 개발/비개발로 나눠 보여준다 — 20개를 한 덩어리로 늘어놓으면 비개발 지원자가 자기 직군을
 * 찾기 전에 개발 직무만 훑고 "여긴 개발자용이구나" 하고 나간다. 실제로 선택지가 개발 4종뿐일
 * 때가 그랬다.
 */
export function JobCategorySelector({
  value,
  onToggle,
}: {
  value: JobCategory[]
  onToggle: (value: JobCategory) => void
}) {
  return (
    <div className="space-y-4">
      <section className="space-y-2">
        <h4 className="text-[13px] font-semibold text-fg-muted">개발</h4>
        <CheckboxCardGroup
          ariaLabel="개발 직군"
          options={[...ENGINEERING_JOB_CATEGORIES]}
          value={value}
          onToggle={onToggle}
        />
      </section>
      <section className="space-y-2">
        <h4 className="text-[13px] font-semibold text-fg-muted">비개발</h4>
        <CheckboxCardGroup
          ariaLabel="비개발 직군"
          options={[...NON_ENGINEERING_JOB_CATEGORIES]}
          value={value}
          onToggle={onToggle}
        />
      </section>
    </div>
  )
}
