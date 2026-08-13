import { useRef, useState } from 'react'
import { isApiError } from '@/shared/api'
import { Spinner } from '@/shared/ui/Spinner'
import { useUploadResume } from '../model/useResumes'

const MAX_FILE_SIZE = 20 * 1024 * 1024

export function ResumeUploader() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
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
    <div>
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
        onDragOver={(e) => {
          e.preventDefault()
          if (!dragging) setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          handleFile(e.dataTransfer.files?.[0])
        }}
        className={[
          'group flex w-full flex-col items-center justify-center gap-4 rounded-xl border-2 border-dashed px-6 py-12 text-center transition-colors duration-fast',
          dragging
            ? 'border-primary bg-primary-50'
            : 'border-border-strong bg-surface-raised hover:border-primary hover:bg-surface',
          'disabled:cursor-not-allowed disabled:opacity-60',
        ].join(' ')}
      >
        <span
          aria-hidden
          className="flex h-14 w-14 items-center justify-center rounded-xl bg-primary-50 text-primary-fg transition-transform duration-fast group-hover:scale-105"
        >
          {upload.isPending ? <Spinner /> : <UploadIcon />}
        </span>
        <span className="space-y-1">
          <span className="block text-body font-semibold text-fg-strong">
            {upload.isPending
              ? '업로드 중…'
              : 'PDF 이력서를 끌어다 놓거나 클릭해 업로드'}
          </span>
          <span className="block text-caption text-fg-muted">
            최대 20MB · PDF 형식만 지원
          </span>
        </span>
      </button>
      {error ? (
        <p className="mt-3 text-caption text-danger-700">{error}</p>
      ) : null}
    </div>
  )
}

function UploadIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="24"
      height="24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 16V4m0 0L7.5 8.5M12 4l4.5 4.5" />
      <path d="M4 15v3.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V15" />
    </svg>
  )
}
