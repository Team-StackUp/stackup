import { Link } from 'react-router-dom'
import { documentSourceLabel } from '@/domain/rag'

export type DocOption = { id: number; label: string; sourceType: string }

export function ContextDocumentPicker({
  documents,
  selected,
  onToggle,
}: {
  documents: DocOption[]
  selected: number[]
  onToggle: (id: number) => void
}) {
  if (documents.length === 0) {
    return (
      <p className="text-caption text-fg-muted">
        분석 완료된 이력서·자소서·레포지토리가 없습니다.{' '}
        <Link
          to="/workspace/resumes"
          className="font-semibold text-primary-fg underline-offset-2 hover:underline"
        >
          자료 준비하기 →
        </Link>
      </p>
    )
  }
  return (
    <div className="flex flex-col gap-2">
      {documents.map((doc) => (
        <label
          key={doc.id}
          htmlFor={`ctx-doc-${doc.id}`}
          className="flex cursor-pointer items-center gap-2 rounded-md border border-border bg-surface-raised px-3 py-2"
        >
          <input
            id={`ctx-doc-${doc.id}`}
            type="checkbox"
            checked={selected.includes(doc.id)}
            onChange={() => onToggle(doc.id)}
          />
          <span className="flex-1 text-button text-fg">{doc.label}</span>
          <span className="rounded-pill bg-surface px-2 py-0.5 text-caption text-fg-muted">
            {documentSourceLabel(doc.sourceType)}
          </span>
        </label>
      ))}
    </div>
  )
}
