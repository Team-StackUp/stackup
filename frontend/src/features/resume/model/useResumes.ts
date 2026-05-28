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
    },
  })
}
