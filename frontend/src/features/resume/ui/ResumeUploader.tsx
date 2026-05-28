import { useRef, useState } from 'react'
import { isApiError } from '@/shared/api'
import { useUploadResume } from '../model/useResumes'

const MAX_FILE_SIZE = 20 * 1024 * 1024

export function ResumeUploader() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const upload = useUploadResume()

  const handleFile = (file: File | undefined) => {
    setError(null)
    if (!file) return
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      setError('PDF 파일만 업로드할 수 있습니다.')
      return
    }
    if (file.size > MAX_FILE_SIZE) {
      setError('파일이 20MB를 초과합니다.')
      return
    }
    upload.mutate(file, {
      onError: (e) =>
        setError(isApiError(e) ? e.message : '업로드에 실패했습니다.'),
      onSuccess: () => {
        if (inputRef.current) inputRef.current.value = ''
      },
    })
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <input
        ref={inputRef}
        type="file"
        accept="application/pdf,.pdf"
        className="hidden"
        onChange={(e) => handleFile(e.target.files?.[0])}
      />
      <button
        type="button"
        disabled={upload.isPending}
        onClick={() => inputRef.current?.click()}
        className="rounded-lg bg-primary px-4 py-2 text-button text-fg-on-primary transition-colors hover:bg-primary-hover disabled:opacity-60"
      >
        {upload.isPending ? '업로드 중…' : 'PDF 업로드'}
      </button>
      {error ? <p className="text-caption text-danger-700">{error}</p> : null}
    </div>
  )
}
