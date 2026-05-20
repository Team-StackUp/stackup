import { useResumeUpload } from '@/features/resume/model/useResumeUpload'

type Props = {
  onError?: (message: string) => void
}

export function UploadResumeButton({ onError }: Props) {
  const { inputRef, accept, error, isPending, openPicker, onInputChange } =
    useResumeUpload({ onError })

  return (
    <div className="flex flex-col items-end gap-1">
      <button
        type="button"
        onClick={openPicker}
        disabled={isPending}
        aria-busy={isPending}
        className="inline-flex items-center gap-2 px-4 py-2 rounded-pill bg-sage-900 text-white text-button hover:bg-sage-800 transition-colors duration-fast disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {isPending ? '업로드 중…' : 'PDF 업로드'}
      </button>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="sr-only"
        onChange={onInputChange}
      />
      {error ? (
        <p role="alert" className="text-caption text-danger-700">
          {error}
        </p>
      ) : null}
    </div>
  )
}
