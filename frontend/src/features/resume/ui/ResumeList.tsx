import { isApiError } from '@/shared/api'
import {
  useDeleteResumeMutation,
  useResumesQuery,
} from '@/features/resume/model/useResumes'
import { formatFileSize } from '@/features/resume/lib/file-validation'
import type {
  ResumeResponse,
  ResumeStatus,
} from '@/features/resume/model/types'
import { ResumeDropzone } from './ResumeDropzone'

export function ResumeList() {
  const { data, isLoading, isError, error, refetch } = useResumesQuery()

  if (isLoading) return <ResumeListSkeleton />
  if (isError) {
    const message = isApiError(error)
      ? error.message
      : '이력서 목록을 불러오지 못했습니다.'
    return (
      <div className="rounded-xl border border-border bg-surface-raised p-8 text-center">
        <p role="alert" className="text-body text-danger-700">
          {message}
        </p>
        <button
          type="button"
          onClick={() => refetch()}
          className="mt-4 inline-flex items-center px-4 py-2 rounded-pill bg-sage-900 text-white text-button hover:bg-sage-800 transition-colors duration-fast"
        >
          다시 시도
        </button>
      </div>
    )
  }

  const resumes = data?.content ?? []
  if (resumes.length === 0) return <ResumeDropzone />

  return (
    <ul className="grid gap-3">
      {resumes.map((resume) => (
        <li key={resume.id}>
          <ResumeCard resume={resume} />
        </li>
      ))}
    </ul>
  )
}

function ResumeCard({ resume }: { resume: ResumeResponse }) {
  const remove = useDeleteResumeMutation()
  const handleDelete = () => {
    // TODO: shared/ui/ConfirmDialog 도입 시 교체 (docs/ui-patterns.md §4)
    const ok = window.confirm(
      `'${resume.originalFilename}' 이력서를 삭제하시겠습니까?`,
    )
    if (!ok) return
    remove.mutate(resume.id)
  }
  return (
    <article className="rounded-xl bg-surface-raised border border-border shadow-sm p-5 flex items-center gap-4">
      <FileIcon />
      <div className="min-w-0 flex-1">
        <p className="text-body text-fg-strong font-semibold truncate">
          {resume.originalFilename}
        </p>
        <p className="text-caption text-fg-muted mt-1">
          {formatFileSize(resume.fileSize)} · {relativeTime(resume.createdAt)}
        </p>
      </div>
      <StatusBadge status={resume.status} />
      <button
        type="button"
        onClick={handleDelete}
        disabled={remove.isPending}
        aria-busy={remove.isPending}
        aria-label={`${resume.originalFilename} 삭제`}
        className="inline-flex items-center px-3 py-1.5 rounded-pill border border-border text-button text-fg-muted hover:text-danger-700 hover:border-danger-500 transition-colors duration-fast disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {remove.isPending ? '삭제 중…' : '삭제'}
      </button>
    </article>
  )
}

function ResumeListSkeleton() {
  return (
    <ul aria-busy="true" aria-label="이력서 목록 로딩 중" className="grid gap-3">
      {[0, 1, 2].map((i) => (
        <li
          key={i}
          className="rounded-xl bg-surface-raised border border-border shadow-sm p-5 flex items-center gap-4"
        >
          <div className="w-10 h-10 rounded-md bg-sage-100 animate-pulse" />
          <div className="flex-1 space-y-2">
            <div className="h-4 w-1/2 rounded bg-sage-100 animate-pulse" />
            <div className="h-3 w-1/3 rounded bg-sage-100 animate-pulse" />
          </div>
          <div className="h-6 w-20 rounded-pill bg-sage-100 animate-pulse" />
        </li>
      ))}
    </ul>
  )
}

const STATUS_LABEL: Record<ResumeStatus, string> = {
  PENDING: '분석 대기',
  ANALYZING: '분석 중',
  ANALYZED: '분석 완료',
  FAILED: '분석 실패',
}

const STATUS_CLASS: Record<ResumeStatus, string> = {
  PENDING: 'bg-info-50 text-info-700',
  ANALYZING: 'bg-info-50 text-info-700',
  ANALYZED: 'bg-success-50 text-success-700',
  FAILED: 'bg-danger-50 text-danger-700',
}

function StatusBadge({ status }: { status: ResumeStatus }) {
  const inProgress = status === 'PENDING' || status === 'ANALYZING'
  return (
    <span
      className={[
        'inline-flex items-center gap-1.5 px-3 py-1 rounded-pill text-caption font-mono',
        STATUS_CLASS[status],
      ].join(' ')}
    >
      {inProgress ? <Spinner /> : null}
      {STATUS_LABEL[status]}
    </span>
  )
}

function Spinner() {
  return (
    <svg
      aria-hidden
      width="10"
      height="10"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="3"
      className="animate-spin"
    >
      <circle cx="12" cy="12" r="9" strokeOpacity="0.25" />
      <path d="M21 12a9 9 0 0 0-9-9" strokeLinecap="round" />
    </svg>
  )
}

function FileIcon() {
  return (
    <div className="w-10 h-10 rounded-md bg-sage-100 text-fg-strong flex items-center justify-center font-mono text-caption font-bold">
      PDF
    </div>
  )
}

function relativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime()
  if (diffMs < 0) return '방금 전'
  const sec = diffMs / 1000
  if (sec < 60) return '방금 전'
  const min = sec / 60
  if (min < 60) return `${Math.floor(min)}분 전`
  const hr = min / 60
  if (hr < 24) return `${Math.floor(hr)}시간 전`
  const day = hr / 24
  if (day < 7) return `${Math.floor(day)}일 전`
  return new Date(iso).toLocaleDateString('ko-KR')
}
