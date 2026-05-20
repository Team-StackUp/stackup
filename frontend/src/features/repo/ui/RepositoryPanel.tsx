import { useState } from 'react'
import { useRegisteredReposQuery } from '@/features/repo/model/useRepos'
import { CandidateRepositoryList } from './CandidateRepositoryList'
import { RegisteredRepositoryList } from './RegisteredRepositoryList'

type Tab = 'registered' | 'candidates'

export function RepositoryPanel() {
  const [tab, setTab] = useState<Tab>('registered')
  const { data } = useRegisteredReposQuery()
  const registeredCount = data?.totalElements ?? 0

  return (
    <div className="space-y-4">
      <div
        role="tablist"
        aria-label="레포지토리 보기"
        className="inline-flex items-center gap-1 p-1 rounded-pill bg-surface border border-border"
      >
        <TabButton
          active={tab === 'registered'}
          onClick={() => setTab('registered')}
          controls="repo-panel-registered"
        >
          등록됨{registeredCount > 0 ? ` (${registeredCount})` : ''}
        </TabButton>
        <TabButton
          active={tab === 'candidates'}
          onClick={() => setTab('candidates')}
          controls="repo-panel-candidates"
        >
          GitHub에서 가져오기
        </TabButton>
      </div>

      {tab === 'registered' ? (
        <div id="repo-panel-registered" role="tabpanel">
          <RegisteredRepositoryList
            onBrowseCandidates={() => setTab('candidates')}
          />
        </div>
      ) : (
        <div id="repo-panel-candidates" role="tabpanel">
          <CandidateRepositoryList />
        </div>
      )}
    </div>
  )
}

function TabButton({
  active,
  onClick,
  controls,
  children,
}: {
  active: boolean
  onClick: () => void
  controls: string
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      aria-controls={controls}
      onClick={onClick}
      className={[
        'inline-flex items-center px-4 py-1.5 rounded-pill text-button transition-colors duration-fast',
        active
          ? 'bg-sage-900 text-white'
          : 'text-fg-muted hover:text-fg-strong',
      ].join(' ')}
    >
      {children}
    </button>
  )
}
