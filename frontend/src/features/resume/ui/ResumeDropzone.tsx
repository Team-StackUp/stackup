import { useEffect, useRef, useState } from 'react'
import { isApiError } from '@/shared/api'
import { useUploadResumeMutation } from '@/features/resume/model/useResumes'
import {
  RESUME_ACCEPT_EXTENSION,
  RESUME_ACCEPT_MIME,
  validateResumeFile,
} from '@/features/resume/lib/file-validation'

const FAKE_PROGRESS_DURATION_MS = 1_500

export function ResumeDropzone() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const upload = useUploadResumeMutation()
  const progress = useFakeProgress(upload.isPending)

  const openPicker = () => inputRef.current?.click()

  const handleFile = (file: File) => {
    setError(null)
    const invalid = validateResumeFile(file)
    if (invalid) {
      setError(invalid.message)
      return
    }
    upload.mutate(file, {
      onError: (err) => {
        setError(
          isApiError(err) ? err.message : '업로드 중 오류가 발생했습니다.',
        )
      },
    })
  }

  const onDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files?.[0]
    if (file) handleFile(file)
  }

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) handleFile(file)
    e.target.value = ''
  }

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label="PDF 이력서 업로드"
      aria-busy={upload.isPending}
      onClick={openPicker}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          openPicker()
        }
      }}
      onDragOver={(e) => {
        e.preventDefault()
        setDragOver(true)
      }}
      onDragLeave={() => setDragOver(false)}
      onDrop={onDrop}
      className={[
        'relative rounded-xl border border-dashed p-10 text-center cursor-pointer transition-colors duration-fast',
        dragOver
          ? 'border-primary bg-surface'
          : 'border-border-strong bg-surface hover:border-primary/60',
        upload.isPending ? 'pointer-events-none' : '',
      ].join(' ')}
    >
      <input
        ref={inputRef}
        type="file"
        accept={`${RESUME_ACCEPT_MIME},${RESUME_ACCEPT_EXTENSION}`}
        className="sr-only"
        onChange={onChange}
      />
      <p className="text-body text-fg-strong font-semibold">
        PDF 이력서를 드래그하거나 클릭해서 업로드
      </p>
      <p className="text-caption text-fg-subtle mt-2">
        최대 20MB · PDF 형식만 지원
      </p>
      {error ? (
        <p
          role="alert"
          className="text-caption text-danger-700 mt-4"
        >
          {error}
        </p>
      ) : null}
      {upload.isPending ? (
        <div className="mt-6">
          <div className="h-1.5 w-full rounded-pill bg-sage-100 overflow-hidden">
            <div
              className="h-full bg-primary transition-[width] duration-fast"
              style={{ width: `${progress}%` }}
            />
          </div>
          <p className="text-caption text-fg-muted mt-2">
            업로드 중… {Math.floor(progress)}%
          </p>
        </div>
      ) : null}
    </div>
  )
}

function useFakeProgress(active: boolean): number {
  const [progress, setProgress] = useState(0)
  useEffect(() => {
    if (!active) {
      setProgress(0)
      return
    }
    const start = Date.now()
    const id = window.setInterval(() => {
      const elapsed = Date.now() - start
      const p = Math.min(100, (elapsed / FAKE_PROGRESS_DURATION_MS) * 100)
      setProgress(p)
      if (p >= 100) window.clearInterval(id)
    }, 60)
    return () => window.clearInterval(id)
  }, [active])
  return progress
}
