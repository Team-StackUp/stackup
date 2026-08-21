import { useState } from 'react'
import { useAuth, useDeleteAccount, useLogout } from '@/features/auth'
import { Button } from '@/shared/ui/Button'
import { ConfirmDialog } from '@/shared/ui'

// 탈퇴가 실제로 무엇을 하고 무엇을 하지 않는지 (docs/security.md §5.3).
// "정말 삭제할까요?" 만 묻고 넘어가면 사용자는 GitHub 권한이 남는다는 걸 끝내 모른다.
const CONSEQUENCES = [
  '면접 기록·피드백·오답노트를 포함한 모든 데이터에 더 이상 접근할 수 없습니다.',
  '발급했던 피드백 공유 링크가 즉시 만료됩니다.',
  '보관 중이던 GitHub 접근 권한(access token)을 즉시 폐기합니다.',
  '같은 계정으로 다시 가입할 수 있지만, 이전 데이터는 복구되지 않습니다.',
]

export function AccountView() {
  const { user } = useAuth()
  const { logout, loggingOut } = useLogout()
  const { remove, deleting } = useDeleteAccount()
  const [confirmOpen, setConfirmOpen] = useState(false)

  return (
    <div className="flex flex-col gap-10">
      <section className="flex flex-col gap-3">
        <h2 className="text-body font-bold tracking-[-0.02em] text-fg">계정</h2>
        <dl className="flex flex-col gap-2 rounded-xl border border-border bg-surface-raised p-5">
          <div className="flex items-baseline justify-between gap-4">
            <dt className="text-caption text-fg-subtle">이름</dt>
            <dd className="text-body text-fg">{user?.displayName ?? '—'}</dd>
          </div>
          <div className="flex items-baseline justify-between gap-4">
            <dt className="text-caption text-fg-subtle">이메일</dt>
            <dd className="text-body text-fg">{user?.email ?? '비공개'}</dd>
          </div>
        </dl>
        <div>
          <Button variant="secondary" onClick={logout} loading={loggingOut}>
            로그아웃
          </Button>
        </div>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-body font-bold tracking-[-0.02em] text-fg">회원 탈퇴</h2>
        <div className="flex flex-col gap-4 rounded-xl border border-danger bg-surface-raised p-5">
          <div className="flex flex-col gap-2">
            <p className="text-caption text-fg-muted">탈퇴하면 다음과 같이 처리됩니다.</p>
            <ul className="flex list-disc flex-col gap-1 pl-5 text-caption text-fg-muted">
              {CONSEQUENCES.map((line) => (
                <li key={line} style={{ wordBreak: 'keep-all' }}>
                  {line}
                </li>
              ))}
            </ul>
          </div>
          {/* 우리가 지우는 건 우리가 가진 사본뿐이다. GitHub 계정 쪽 승인은 사용자만
              해제할 수 있으므로 어디서 하는지까지 알려준다. */}
          <p className="text-caption text-fg-subtle" style={{ wordBreak: 'keep-all' }}>
            StackUp 에 준 GitHub 앱 권한 자체를 취소하려면{' '}
            <a
              href="https://github.com/settings/applications"
              target="_blank"
              rel="noreferrer noopener"
              className="text-primary-fg underline underline-offset-2"
            >
              GitHub 설정 &gt; Authorized OAuth Apps
            </a>
            에서 함께 해제해 주세요.
          </p>
          <div>
            <Button variant="danger" onClick={() => setConfirmOpen(true)} loading={deleting}>
              회원 탈퇴
            </Button>
          </div>
        </div>
      </section>

      <ConfirmDialog
        open={confirmOpen}
        danger
        loading={deleting}
        title="정말 탈퇴하시겠어요?"
        description="면접 기록과 피드백을 포함한 모든 데이터에 더 이상 접근할 수 없습니다. 이 작업은 되돌릴 수 없습니다."
        confirmLabel="탈퇴하기"
        cancelLabel="취소"
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => {
          void remove().then((done) => {
            if (!done) setConfirmOpen(false)
          })
        }}
      />
    </div>
  )
}
