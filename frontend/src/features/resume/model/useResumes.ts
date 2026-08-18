import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isApiError } from '@/shared/api'
import { toast } from '@/shared/ui'
import {
  deleteResume,
  fetchResumes,
  registerWebResume,
  uploadResume,
} from '../api/resume'
import type { Resume } from './types'

const errMessage = (e: unknown, fallback: string) =>
  isApiError(e) ? e.message : fallback

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
      toast.success('이력서를 업로드했어요. 분석이 곧 시작됩니다.')
    },
  })
}

export function useRegisterWebResume() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: registerWebResume,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: resumeKeys.all })
      toast.success('링크를 등록했어요. 분석이 곧 시작됩니다.')
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
      toast.success('이력서를 삭제했어요')
    },
    onError: (e) => toast.error(errMessage(e, '이력서 삭제에 실패했어요')),
  })
}
