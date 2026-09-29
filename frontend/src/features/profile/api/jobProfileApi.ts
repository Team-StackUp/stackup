import { apiClient } from '@/shared/api'
import type { JobCategory } from '@/domain/session'
import type { CareerLevel } from '@/domain/session/model/careerLevel'

export type JobProfile = {
  desiredJobCategories: JobCategory[]
  desiredIndustry: string | null
  careerLevel: CareerLevel | null
  /** 한 번이라도 채웠는가. 가입 직후 안내를 띄울지 판단한다. */
  filled: boolean
}

export async function fetchJobProfile(): Promise<JobProfile> {
  const { data } = await apiClient.get<JobProfile>('/api/users/me/job-profile')
  return data
}

/**
 * 생략한(undefined) 항목은 서버가 그대로 둔다. 비우려면 빈 목록 / 빈 문자열을 보낸다 —
 * 계정 화면이 일부만 보내도 나머지가 날아가지 않아야 한다.
 */
export async function saveJobProfile(patch: {
  desiredJobCategories?: JobCategory[]
  desiredIndustry?: string
  careerLevel?: CareerLevel
}): Promise<JobProfile> {
  const { data } = await apiClient.put<JobProfile>('/api/users/me/job-profile', patch)
  return data
}
