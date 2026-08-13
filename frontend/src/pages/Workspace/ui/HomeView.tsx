import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  ScoreTrend,
  StatsSummary,
  useUserStats,
} from '@/features/history'
import { Eyebrow, Heading, Reveal } from '@/shared/ui'

export function HomeView() {
  // stats 는 홈에서 보조 블록(최근 점수)이라 실패해도 화면 자체는 성립한다.
  // 전용 화면인 HistoryView 는 실패를 명시적으로 알린다(QueryError).
  const { data: stats } = useUserStats()
  const hasSessions = (stats?.totalSessionCount ?? 0) > 0

  return (
    <div className="space-y-12">
      {/*
        기존엔 sage-600 단색 배너 + 흰 글자였다. 랜딩 어디에도 없는 색면이라
        워크스페이스만 다른 제품처럼 보였고, 고정 톤이라 다크모드에서 반전되지도 않았다.
        랜딩 히어로와 같은 방식 — 헤어라인으로 끊고 조판으로만 무게를 준다.
      */}
      <Reveal as="section">
        <Eyebrow tone="brand">맞춤 모의 면접</Eyebrow>
        <Heading level="section" className="mt-3 max-w-xl">
          이력서·레포 기반 맞춤 면접을 시작하세요
        </Heading>
        <p
          className="mt-3 max-w-xl text-body font-normal text-fg-muted"
          style={{ wordBreak: 'keep-all' }}
        >
          면접 모드와 직군을 고르면 AI가 질문을 생성하고, 실시간으로 답변을 주고받습니다.
        </p>
        <div className="mt-7 flex flex-wrap items-center gap-x-6 gap-y-3">
          <Link
            to="/sessions/new"
            className="inline-flex items-center gap-2.5 rounded-xl bg-primary px-6 py-3.5 text-body font-semibold text-fg-on-primary transition-colors duration-fast hover:bg-primary-hover"
          >
            <PlayIcon />
            새 면접 시작
          </Link>
          <Link
            to="/workspace/resumes"
            className="group inline-flex items-center gap-1 px-1 py-3.5 text-body font-semibold text-fg-strong transition-colors duration-fast hover:text-primary-fg"
          >
            자료 준비하기
            <span
              aria-hidden
              className="transition-transform duration-fast group-hover:translate-x-0.5"
            >
              →
            </span>
          </Link>
        </div>
      </Reveal>

      <div className="grid gap-4 border-t border-border pt-8 sm:grid-cols-2 lg:grid-cols-4">
        <QuickLink
          to="/workspace/resumes"
          title="이력서"
          description="이력서를 업로드하고 분석 결과를 확인하세요."
          icon={<ResumeIcon />}
        />
        <QuickLink
          to="/workspace/cover-letters"
          title="자소서"
          description="자소서 문항을 입력하고 분석 결과를 확인하세요."
          icon={<CoverLetterIcon />}
        />
        <QuickLink
          to="/workspace/repos"
          title="레포지토리"
          description="GitHub 레포를 등록하고 분석 결과를 확인하세요."
          icon={<RepoIcon />}
        />
        <QuickLink
          to="/workspace/history"
          title="면접 히스토리"
          description="지난 면접 기록과 점수 추이를 확인하세요."
          icon={<HistoryIcon />}
        />
      </div>

      {hasSessions && stats && (
        <section className="border-t border-border pt-8">
          <Eyebrow as="h2">한눈에 보기</Eyebrow>
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            <StatsSummary stats={stats} />
            <ScoreTrend stats={stats} />
          </div>
        </section>
      )}
    </div>
  )
}

function QuickLink({
  to,
  title,
  description,
  icon,
}: {
  to: string
  title: string
  description: string
  icon: ReactNode
}) {
  return (
    <Link
      to={to}
      className="group flex flex-col gap-4 rounded-xl border border-border bg-surface-raised p-5 transition-colors duration-fast hover:border-border-strong"
    >
      <div className="flex items-center justify-between">
        <span
          aria-hidden
          className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-50 text-primary-fg"
        >
          {icon}
        </span>
        <span
          aria-hidden
          className="text-fg-subtle transition-transform duration-fast group-hover:translate-x-0.5"
        >
          →
        </span>
      </div>
      <div>
        <Heading level="sub" as="h3">
          {title}
        </Heading>
        <p className="mt-1.5 text-caption text-fg-muted" style={{ wordBreak: 'keep-all' }}>
          {description}
        </p>
      </div>
    </Link>
  )
}

function PlayIcon() {
  return (
    <svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor" aria-hidden>
      <path d="M6 4.2v11.6a1 1 0 0 0 1.52.85l9.2-5.8a1 1 0 0 0 0-1.7l-9.2-5.8A1 1 0 0 0 6 4.2Z" />
    </svg>
  )
}

function ResumeIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="20"
      height="20"
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

function CoverLetterIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <rect x="3" y="3" width="14" height="14" rx="1.5" />
      <path d="M6.5 7.5h7M6.5 10.5h7M6.5 13.5h4" />
    </svg>
  )
}

function RepoIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="20"
      height="20"
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

function HistoryIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="10" cy="10" r="7.5" />
      <path d="M10 5.5V10l3 2" />
    </svg>
  )
}
