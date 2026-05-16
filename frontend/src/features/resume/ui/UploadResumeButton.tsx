import { useRef, useState } from 'react'
import { isApiError } from '@/shared/api'
import { useUploadResumeMutation } from '@/features/resume/model/useResumes'
import {
  RESUME_ACCEPT_EXTENSION,
  RESUME_ACCEPT_MIME,
  validateResumeFile,
} from '@/features/resume/lib/file-validation'

type Props = {
  onError?: (message: string) => void
}

export function UploadResumeButton({ onError }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const upload = useUploadResumeMutation()
  const [localError, setLocalError] = useState<string | null>(null)

  const handle = (file: File) => {
    setLocalError(null)
    const invalid = validateResumeFile(file)
    if (invalid) {
      setLocalError(invalid.message)
      onError?.(invalid.message)
      return
    }
    upload.mutate(file, {
      onError: (err) => {
        const message = isApiError(err)
          ? err.message
          : '업로드 중 오류가 발생했습니다.'
        setLocalError(message)
        onError?.(message)
      },
    })
  }

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) handle(file)
    e.target.value = ''
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={upload.isPending}
        aria-busy={upload.isPending}
        className="inline-flex items-center gap-2 px-4 py-2 rounded-pill bg-sage-900 text-white text-button hover:bg-sage-800 transition-colors duration-fast disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {upload.isPending ? '업로드 중…' : 'PDF 업로드'}
      </button>
      <input
        ref={inputRef}
        type="file"
        accept={`${RESUME_ACCEPT_MIME},${RESUME_ACCEPT_EXTENSION}`}
        className="sr-only"
        onChange={onChange}
      />
      {localError ? (
        <p role="alert" className="text-caption text-danger-700">
          {localError}
        </p>
      ) : null}
    </div>
  )
}
