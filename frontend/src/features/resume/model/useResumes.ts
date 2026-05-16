import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  deleteResume,
  getResume,
  listResumes,
  uploadResume,
} from '@/features/resume/api/resume'
import type {
  PageResponse,
  ResumeResponse,
} from '@/features/resume/model/types'

const RESUMES_KEY = 'resumes' as const
const POLL_INTERVAL_MS = 2_000

function hasInProgress(page: PageResponse<ResumeResponse> | undefined): boolean {
  if (!page) return false
  return page.content.some(
    (r) => r.status === 'PENDING' || r.status === 'ANALYZING',
  )
}

export function useResumesQuery(page = 0, size = 20) {
  return useQuery({
    queryKey: [RESUMES_KEY, page, size],
    queryFn: () => listResumes(page, size),
    refetchInterval: (query) =>
      hasInProgress(query.state.data) ? POLL_INTERVAL_MS : false,
  })
}

export function useResumeQuery(id: number | null) {
  return useQuery({
    queryKey: [RESUMES_KEY, 'detail', id],
    queryFn: () => getResume(id as number),
    enabled: id !== null,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'PENDING' || status === 'ANALYZING'
        ? POLL_INTERVAL_MS
        : false
    },
  })
}

export function useUploadResumeMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => uploadResume(file),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [RESUMES_KEY] })
    },
  })
}

export function useDeleteResumeMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteResume(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [RESUMES_KEY] })
    },
  })
}
