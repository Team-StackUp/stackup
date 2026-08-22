import { useState } from 'react'
import { EmptyState, ListSkeleton, Markdown, Modal, StatusBadge, type StatusTone, QueryError } from '@/shared/ui'
import { DOCUMENT_SOURCE_LABEL as SOURCE_LABEL } from '@/domain/rag'
import type { DocumentFilter } from '../api/analysis'
import { useDocuments } from '../model/useDocuments'
import { useDocumentMarkdown } from '../model/useDocumentMarkdown'
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

const TECH_PREVIEW_COUNT = 4

type Props = {
  filter?: DocumentFilter
  // 클라이언트에서 소스 유형으로 한정 (탭별 분석 결과 분리용).
  sourceType?: AnalysisSourceType
}

export function DocumentList({ filter = {}, sourceType }: Props) {
  const { data = [], isPending, isError, refetch } = useDocuments(filter)
  const [activeId, setActiveId] = useState<number | null>(null)

  if (isPending) {
    return <ListSkeleton label="분석 결과를 불러오는 중…" />
  }
  if (isError) {
    return <QueryError message="분석 결과를 불러오지 못했습니다." onRetry={() => refetch()} />
  }

  const docs = sourceType
    ? data.filter((doc) => doc.sourceType === sourceType)
    : data

  if (docs.length === 0) {
    const subject = sourceType ? SOURCE_LABEL[sourceType] : '이력서·레포'
    return (
      <EmptyState
        title="아직 분석된 문서가 없어요"
        description={`${subject} 분석이 완료되면 요약과 기술 스택이 여기에 표시됩니다.`}
      />
    )
  }

  const activeDoc = docs.find((doc) => doc.id === activeId) ?? null

  return (
    <>
      <ul className="grid gap-3 sm:grid-cols-2">
        {docs.map((doc) => (
          <DocumentCard
            key={doc.id}
            doc={doc}
            onOpen={() => setActiveId(doc.id)}
          />
        ))}
      </ul>

      <Modal
        open={activeDoc !== null}
        onClose={() => setActiveId(null)}
        title={activeDoc ? `${SOURCE_LABEL[activeDoc.sourceType]} 분석 결과` : ''}
      >
        {activeDoc ? <DocumentDetail key={activeDoc.id} doc={activeDoc} /> : null}
      </Modal>
    </>
  )
}

function DocumentCard({
  doc,
  onOpen,
}: {
  doc: AnalyzedDocument
  onOpen: () => void
}) {
  const meta = STATUS_META[doc.analysisStatus]
  const clickable = doc.analysisStatus === 'ANALYZED'
  const extraTech = doc.techStack.length - TECH_PREVIEW_COUNT

  const body = (
    <>
      <div className="flex items-center gap-3">
        <span
          aria-hidden
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-50 text-primary-fg"
        >
          <SourceIcon source={doc.sourceType} />
        </span>
        <span className="flex-1 font-mono text-caption tracking-tight text-fg-subtle">
          {SOURCE_LABEL[doc.sourceType]}
        </span>
        <StatusBadge tone={meta.tone}>{meta.label}</StatusBadge>
      </div>

      {doc.analysisStatus === 'ANALYZED' ? (
        <>
          {doc.summary ? (
            <p className="mt-3 line-clamp-2 text-body leading-relaxed text-fg-strong">
              {doc.summary}
            </p>
          ) : null}
          {doc.techStack.length > 0 ? (
            <ul className="mt-3 flex flex-wrap items-center gap-1.5">
              {doc.techStack.slice(0, TECH_PREVIEW_COUNT).map((tech) => (
                <li
                  key={tech}
                  className="rounded-pill border border-border bg-surface px-2.5 py-0.5 text-caption font-medium text-fg-strong"
                >
                  {tech}
                </li>
              ))}
              {extraTech > 0 ? (
                <li className="text-caption text-fg-muted">+{extraTech}</li>
              ) : null}
            </ul>
          ) : null}
        </>
      ) : null}

      {doc.analysisStatus === 'PROCESSING' ? (
        <p className="mt-3 text-caption text-fg-muted">
          분석이 완료되면 요약과 기술 스택이 표시됩니다.
        </p>
      ) : null}

      {doc.analysisStatus === 'FAILED' ? (
        <p className="mt-3 text-caption text-danger-700">
          {doc.errorMessage ?? '분석에 실패했습니다.'}
        </p>
      ) : null}
    </>
  )

  if (!clickable) {
    return (
      <li className="rounded-xl border border-border bg-surface-raised p-5">
        {body}
      </li>
    )
  }

  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        className="group flex w-full flex-col rounded-xl border border-border bg-surface-raised p-5 text-left transition-colors duration-fast hover:border-border-strong"
      >
        {body}
        <span className="mt-3 inline-flex items-center gap-1 text-caption font-semibold text-primary-fg">
          자세히 보기
          <span
            aria-hidden
            className="transition-transform duration-fast group-hover:translate-x-0.5"
          >
            →
          </span>
        </span>
      </button>
    </li>
  )
}

function DocumentDetail({ doc }: { doc: AnalyzedDocument }) {
  // 분석 원문(마크다운)은 열람 요청 시에만 로드 — 모달 오픈만으로 presigned 발급을 낭비하지 않는다.
  const [showRaw, setShowRaw] = useState(false)
  const markdown = useDocumentMarkdown(doc.id, showRaw)
  return (
    <div className="space-y-5">
      {doc.summary ? (
        <section>
          <h3 className="font-mono text-caption tracking-tight text-fg-subtle">
            요약
          </h3>
          <p className="mt-2 whitespace-pre-line text-body leading-relaxed text-fg-strong">
            {doc.summary}
          </p>
        </section>
      ) : null}

      {doc.techStack.length > 0 ? (
        <section>
          <h3 className="font-mono text-caption tracking-tight text-fg-subtle">
            기술 스택
          </h3>
          <ul className="mt-2 flex flex-wrap gap-1.5">
            {doc.techStack.map((tech) => (
              <li
                key={tech}
                className="rounded-pill border border-border bg-surface px-2.5 py-0.5 text-caption font-medium text-fg-strong"
              >
                {tech}
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {doc.analysisStatus === 'ANALYZED' && doc.documentPath ? (
        <section>
          <button
            type="button"
            aria-expanded={showRaw}
            onClick={() => setShowRaw((v) => !v)}
            className="text-caption font-medium text-primary-fg hover:underline"
          >
            {showRaw ? '분석 원문 접기' : '분석 원문 보기'}
          </button>
          {showRaw &&
            (markdown.isPending ? (
              <p className="mt-2 text-caption text-fg-subtle">원문을 불러오는 중…</p>
            ) : markdown.isError ? (
              <QueryError
                message="분석 원문을 불러오지 못했습니다."
                onRetry={() => markdown.refetch()}
              />
            ) : (
              <div className="mt-3 max-w-readable rounded-lg border border-border bg-surface-raised p-4">
                <Markdown className="text-fg">{markdown.data ?? ''}</Markdown>
              </div>
            ))}
        </section>
      ) : null}
    </div>
  )
}

function SourceIcon({ source }: { source: AnalysisSourceType }) {
  if (source === 'REPOSITORY') {
    return (
      <svg
        viewBox="0 0 20 20"
        width="18"
        height="18"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <circle cx="6" cy="5" r="2" />
        <circle cx="6" cy="15" r="2" />
        <circle cx="14" cy="7.5" r="2" />
        <path d="M6 7v6M6 13a4 4 0 0 0 4-4h2.2" />
      </svg>
    )
  }
  if (source === 'WEB') {
    return (
      <svg
        viewBox="0 0 20 20"
        width="18"
        height="18"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <circle cx="10" cy="10" r="7.5" />
        <path d="M2.5 10h15M10 2.5c2 2.2 3 4.8 3 7.5s-1 5.3-3 7.5c-2-2.2-3-4.8-3-7.5s1-5.3 3-7.5Z" />
      </svg>
    )
  }
  return (
    <svg
      viewBox="0 0 20 20"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M5 2.5h6l4 4V16a1.5 1.5 0 0 1-1.5 1.5h-8A1.5 1.5 0 0 1 4 16V4A1.5 1.5 0 0 1 5 2.5Z" />
      <path d="M11 2.5V6a1 1 0 0 0 1 1h3" />
      <path d="M7 10.5h6M7 13.5h4" />
    </svg>
  )
}
