export const RESUME_MAX_BYTES = 20 * 1024 * 1024
export const RESUME_ACCEPT_MIME = 'application/pdf'
export const RESUME_ACCEPT_EXTENSION = '.pdf'

export type ResumeFileErrorCode =
  | 'RESUME_EMPTY_FILE'
  | 'RESUME_INVALID_FILE_TYPE'
  | 'RESUME_FILE_TOO_LARGE'

export type ResumeFileError = {
  code: ResumeFileErrorCode
  message: string
  maxBytes?: number
}

export function validateResumeFile(file: File): ResumeFileError | null {
  if (file.size === 0) {
    return { code: 'RESUME_EMPTY_FILE', message: '빈 파일은 업로드할 수 없습니다.' }
  }
  const isPdfMime = file.type === RESUME_ACCEPT_MIME
  const isPdfExt = file.name.toLowerCase().endsWith(RESUME_ACCEPT_EXTENSION)
  if (!isPdfMime && !isPdfExt) {
    return {
      code: 'RESUME_INVALID_FILE_TYPE',
      message: 'PDF 파일만 업로드할 수 있습니다.',
    }
  }
  if (file.size > RESUME_MAX_BYTES) {
    return {
      code: 'RESUME_FILE_TOO_LARGE',
      message: `파일 크기가 ${Math.floor(RESUME_MAX_BYTES / 1024 / 1024)}MB를 초과합니다.`,
      maxBytes: RESUME_MAX_BYTES,
    }
  }
  return null
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
