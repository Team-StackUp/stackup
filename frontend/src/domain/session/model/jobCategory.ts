import type { JobCategory } from './types'

/**
 * 직군 라벨의 단일 출처.
 *
 * 전에는 선택 화면(JobCategorySelector)과 히스토리 카드(SessionCard)가 각자 자기 목록을
 * 들고 있었다. 직군을 늘릴 때 한쪽만 고치면 **히스토리에는 영어 코드가 그대로 뜬다** —
 * 조용히 틀리는 종류라 한 곳으로 모은다.
 *
 * 서비스 대상이 IT 직군이 아니라 취준생 전반이라, 개발은 세부 직무 단위 / 비개발은 대분류
 * 단위로 둔다. 더 좁은 직무는 '직무 맞춤 모드'(회사명 + 채용공고)가 받는다.
 */
export const ENGINEERING_JOB_CATEGORIES = [
  { value: 'FRONTEND', label: '프론트엔드' },
  { value: 'BACKEND', label: '백엔드' },
  { value: 'INFRA', label: '인프라·DevOps' },
  { value: 'DBA', label: 'DBA' },
  { value: 'MOBILE', label: '모바일' },
  { value: 'DATA_AI', label: '데이터·AI' },
  { value: 'SECURITY', label: '보안' },
  { value: 'QA', label: 'QA·테스트' },
] as const satisfies readonly { value: JobCategory; label: string }[]

export const NON_ENGINEERING_JOB_CATEGORIES = [
  { value: 'PLANNING', label: '기획·PM' },
  { value: 'MARKETING', label: '마케팅·광고' },
  { value: 'SALES', label: '영업·영업관리' },
  { value: 'HR', label: '인사·노무' },
  { value: 'FINANCE', label: '재무·회계' },
  { value: 'DESIGN', label: '디자인' },
  { value: 'MANUFACTURING', label: '생산·품질' },
  { value: 'RND', label: '연구개발' },
  { value: 'CUSTOMER_SERVICE', label: '고객지원·CS' },
  { value: 'LOGISTICS', label: '물류·유통·구매' },
  { value: 'LEGAL', label: '법무' },
  { value: 'PUBLIC', label: '공공·행정' },
] as const satisfies readonly { value: JobCategory; label: string }[]

export const JOB_CATEGORY_LABEL: Record<JobCategory, string> = Object.fromEntries(
  [...ENGINEERING_JOB_CATEGORIES, ...NON_ENGINEERING_JOB_CATEGORIES].map(
    (o) => [o.value, o.label] as const,
  ),
) as Record<JobCategory, string>

/** 개발 직군인가. GitHub 레포처럼 개발자에게만 의미 있는 자료를 안내할 때 쓴다. */
export function isEngineeringJob(value: JobCategory): boolean {
  return ENGINEERING_JOB_CATEGORIES.some((o) => o.value === value)
}
