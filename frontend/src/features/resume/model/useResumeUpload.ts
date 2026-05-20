import { useRef, useState } from 'react'
import { isApiError } from '@/shared/api'
import { useUploadResumeMutation } from '@/features/resume/model/useResumes'
import {
  RESUME_ACCEPT_EXTENSION,
  RESUME_ACCEPT_MIME,
  validateResumeFile,
} from '@/features/resume/lib/file-validation'

const RESUME_ACCEPT = `${RESUME_ACCEPT_MIME},${RESUME_ACCEPT_EXTENSION}`

type Options = {
  onError?: (message: string) => void
}

export function useResumeUpload({ onError }: Options = {}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const upload = useUploadResumeMutation()
  const [error, setError] = useState<string | null>(null)

  const fail = (message: string) => {
    setError(message)
    onError?.(message)
  }

  const handleFile = (file: File) => {
    setError(null)
    const invalid = validateResumeFile(file)
    if (invalid) {
      fail(invalid.message)
      return
    }
    upload.mutate(file, {
      onError: (err) => {
        fail(
          isApiError(err) ? err.message : '업로드 중 오류가 발생했습니다.',
        )
      },
    })
  }

  const openPicker = () => inputRef.current?.click()

  const onInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) handleFile(file)
    e.target.value = ''
  }

  return {
    inputRef,
    accept: RESUME_ACCEPT,
    error,
    isPending: upload.isPending,
    openPicker,
    handleFile,
    onInputChange,
  }
}
