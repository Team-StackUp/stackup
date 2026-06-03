import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { deleteResume, fetchResumes, uploadResume } from '../api/resume'
import type { Resume } from './types'

export const resumeKeys = {
  all: ['resumes'] as const,
}

export function useResumes() {
  return useQuery<Resume[]>({
    queryKey: resumeKeys.all,
    queryFn: fetchResumes,
  })
}

export function useUploadResume() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: uploadResume,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: resumeKeys.all })
    },
  })
}

export function useDeleteResume() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteResume,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: resumeKeys.all })
      // 분석 결과(documents)는 analysis feature 소유라 직접 import 하지 않고(FSD 동일레이어 금지)
      // 키 리터럴 ['documents'] 로 무효화 — 삭제는 클라이언트 액션이라 SSE 가 오지 않는다.
      void queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
  })
}
