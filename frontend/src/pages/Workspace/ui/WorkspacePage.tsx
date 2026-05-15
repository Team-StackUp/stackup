import { useState } from 'react'
import { useAuth } from '@/features/auth'

export default function WorkspacePage() {
  const { user, logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)

  const handleLogout = async () => {
    if (loggingOut) return
    setLoggingOut(true)
    try {
      await logout()
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <main style={{ padding: '2rem' }}>
      <h1>Workspace</h1>
      <p>이력서·레포 관리 화면이 들어갈 자리입니다.</p>

      {user ? (
        <section
          style={{
            margin: '1rem 0',
            padding: '1rem',
            border: '1px solid #ddd',
            borderRadius: 8,
            display: 'inline-flex',
            gap: 12,
            alignItems: 'center',
          }}
        >
          {user.avatarUrl ? (
            <img
              src={user.avatarUrl}
              alt={user.githubUsername}
              width={40}
              height={40}
              style={{ borderRadius: '50%' }}
            />
          ) : null}
          <div style={{ textAlign: 'left' }}>
            <div>
              <strong>@{user.githubUsername}</strong>
            </div>
            <div style={{ color: '#666', fontSize: 14 }}>
              {user.email ?? 'email 없음'}
            </div>
          </div>
        </section>
      ) : null}

      <div>
        <button type="button" onClick={handleLogout} disabled={loggingOut}>
          {loggingOut ? '로그아웃 중…' : '로그아웃'}
        </button>
      </div>
    </main>
  )
}
