import { useState } from 'react'
import { isApiError } from '@/shared/api'
import { StatusBadge, type StatusTone } from '@/shared/ui'
import { fetchDocument, type DocumentFilter } from '../api/analysis'
import { useDocuments } from '../model/useDocuments'
import type {
  AnalysisSourceType,
  AnalysisStatus,
  AnalyzedDocument,
} from '../model/types'

const STATUS_META: Record<AnalysisStatus, { tone: StatusTone; label: string }> =
  {
    PROCESSING: { tone: 'warning', label: '분석 중' },
    ANALYZED: { tone: 'success', label: '분석 완료' },
    FAILED: { tone: 'danger', label: '분석 실패' },
  }

const SOURCE_LABEL: Record<AnalysisSourceType, string> = {
  RESUME: '이력서',
  REPOSITORY: '레포지토리',
  WEB: '웹',
}

type Props = {
  filter?: DocumentFilter
  // 클라이언트에서 소스 유형으로 한정 (탭별 분석 결과 분리용).
  sourceType?: AnalysisSourceType
}

export function DocumentList({ filter = {}, sourceType }: Props) {
  const { data = [], isPending, isError, error } = useDocuments(filter)

  if (isPending) {
    return <p className="text-body text-fg-muted">분석 결과를 불러오는 중…</p>
  }
  if (isError) {
    return (
      <p className="text-body text-danger-700">
        {isApiError(error) ? error.message : '분석 결과를 불러오지 못했습니다.'}
      </p>
    )
  }

  const docs = sourceType
    ? data.filter((doc) => doc.sourceType === sourceType)
    : data

  if (docs.length === 0) {
    const subject = sourceType ? SOURCE_LABEL[sourceType] : '이력서·레포'
    return (
      <div className="rounded-xl border border-dashed border-border-strong bg-surface p-10 text-center">
        <p className="text-body text-fg-muted">아직 분석된 문서가 없습니다.</p>
        <p className="text-caption text-fg-subtle mt-2">
          {subject} 분석이 완료되면 요약과 기술 스택이 여기에 표시됩니다.
        </p>
      </div>
    )
  }

  return (
    <ul className="flex flex-col gap-3">
      {docs.map((doc) => (
        <DocumentRow key={doc.id} doc={doc} />
      ))}
    </ul>
  )
}

function DocumentRow({ doc }: { doc: AnalyzedDocument }) {
  const meta = STATUS_META[doc.analysisStatus]
  const [opening, setOpening] = useState(false)
  const [openError, setOpenError] = useState<string | null>(null)

  const openSource = async () => {
    setOpenError(null)
    setOpening(true)
    try {
      const detail = await fetchDocument(doc.id)
      if (detail.documentDownloadUrl) {
        window.open(detail.documentDownloadUrl, '_blank', 'noreferrer')
      } else {
        setOpenError('다운로드 URL을 가져오지 못했습니다.')
      }
    } catch (e) {
      setOpenError(isApiError(e) ? e.message : '원문을 열지 못했습니다.')
    } finally {
      setOpening(false)
    }
  }

  return (
    <li className="rounded-xl border border-border bg-surface-raised px-4 py-3">
      <div className="flex items-center justify-between gap-4">
        <span className="text-caption text-fg-subtle">
          {SOURCE_LABEL[doc.sourceType]}
        </span>
        <StatusBadge tone={meta.tone}>{meta.label}</StatusBadge>
      </div>

      {doc.analysisStatus === 'ANALYZED' ? (
        <>
          {doc.summary ? (
            <p className="text-body text-fg-strong mt-2">{doc.summary}</p>
          ) : null}
          {doc.techStack.length > 0 ? (
            <ul className="flex flex-wrap gap-1.5 mt-2">
              {doc.techStack.map((tech) => (
                <li
                  key={tech}
                  className="rounded-pill bg-surface px-2 py-0.5 text-caption text-fg-muted"
                >
                  {tech}
                </li>
              ))}
            </ul>
          ) : null}
          <button
            type="button"
            disabled={opening}
            onClick={() => void openSource()}
            className="text-caption text-primary hover:underline disabled:opacity-60 mt-2"
          >
            {opening ? '여는 중…' : '분석 원문 보기'}
          </button>
          {openError ? (
            <p className="text-caption text-danger-700 mt-1">{openError}</p>
          ) : null}
        </>
      ) : null}

      {doc.analysisStatus === 'FAILED' ? (
        <p className="text-caption text-danger-700 mt-2">
          {doc.errorMessage ?? '분석에 실패했습니다.'}
        </p>
      ) : null}
    </li>
  )
}
