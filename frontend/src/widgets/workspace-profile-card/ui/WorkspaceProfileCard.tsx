import { useAuth } from '@/features/auth'

export function WorkspaceProfileCard() {
  const { status, user } = useAuth()

  if (status === 'loading') return <ProfileSkeleton />
  if (!user) return null

  return (
    <section className="rounded-2xl bg-surface-raised border border-border shadow-sm p-6 flex items-center gap-5">
      <Avatar url={user.avatarUrl} name={user.githubUsername} />
      <div className="min-w-0 flex-1">
        <h2 className="font-heading font-bold text-h4 text-fg-strong truncate">
          @{user.githubUsername}
        </h2>
        <p className="text-caption text-fg-muted mt-1 truncate">
          {user.email ?? '이메일 미공개'}
        </p>
        <p className="text-caption text-fg-subtle mt-2 inline-flex items-center gap-1.5">
          <GithubMark />
          GitHub로 연결됨
        </p>
      </div>
    </section>
  )
}

function Avatar({ url, name }: { url: string | null; name: string }) {
  if (url) {
    return (
      <img
        src={url}
        alt={name}
        width={64}
        height={64}
        className="w-16 h-16 rounded-full object-cover border border-border"
      />
    )
  }
  const initial = name.charAt(0).toUpperCase() || '?'
  return (
    <div
      aria-hidden
      className="w-16 h-16 rounded-full bg-sage-100 text-fg-strong font-heading font-bold text-h5 flex items-center justify-center"
    >
      {initial}
    </div>
  )
}

function GithubMark() {
  return (
    <svg
      aria-hidden
      viewBox="0 0 16 16"
      width="12"
      height="12"
      fill="currentColor"
    >
      <path d="M8 0C3.58 0 0 3.58 0 8a8 8 0 0 0 5.47 7.59c.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.42 7.42 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z" />
    </svg>
  )
}

function ProfileSkeleton() {
  return (
    <section
      aria-busy="true"
      aria-label="프로필 로딩 중"
      className="rounded-2xl bg-surface-raised border border-border shadow-sm p-6 flex items-center gap-5"
    >
      <div className="w-16 h-16 rounded-full bg-sage-100 animate-pulse" />
      <div className="flex-1 space-y-3">
        <div className="h-5 w-40 rounded bg-sage-100 animate-pulse" />
        <div className="h-3 w-56 rounded bg-sage-100 animate-pulse" />
      </div>
    </section>
  )
}
