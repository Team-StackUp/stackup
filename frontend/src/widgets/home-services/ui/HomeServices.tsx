import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Modal, Reveal } from '@/shared/ui'

type ServiceTile = {
  plan: 'Pro' | 'Free'
  title: string
  tags: string[]
} & ({ to: string } | { modal: 'role' })

/** 타일에는 라벨만 — 항목마다 설명을 붙이지 않는다. */
const services: ServiceTile[] = [
  {
    plan: 'Pro',
    title: '이력서 심층 면접',
    tags: ['이력서', '자소서', 'GitHub'],
    to: '/sessions/new',
  },
  {
    plan: 'Free',
    title: '직무 기술 면접',
    tags: ['프론트엔드', '백엔드'],
    modal: 'role',
  },
  {
    plan: 'Free',
    title: 'CS 전공 지식 면접',
    tags: ['OS', '네트워크', 'DB'],
    to: '/practice/cs',
  },
]

const ROLE_OPTIONS = [
  { track: 'frontend', label: '프론트엔드', desc: 'HTML·CSS·JS·React·브라우저' },
  { track: 'backend', label: '백엔드', desc: 'OS·네트워크·DB·자바·스프링' },
] as const

const tileClass =
  'group flex h-full w-full flex-col justify-between rounded-xl border border-border bg-surface-raised p-5 text-left transition-colors duration-fast hover:border-border-strong'

function TileInner({ tile }: { tile: ServiceTile }) {
  return (
    <>
      <div className="flex items-center justify-between gap-3">
        <span
          className={`rounded-pill px-2 py-0.5 text-caption font-semibold ${
            tile.plan === 'Pro' ? 'bg-primary-100 text-primary-fg' : 'bg-surface text-fg-muted'
          }`}
        >
          {tile.plan}
        </span>
        <span
          aria-hidden
          className="text-fg-subtle transition-transform duration-fast group-hover:translate-x-1"
        >
          →
        </span>
      </div>

      <div className="mt-10">
        <h3
          className="font-sans font-bold text-fg"
          style={{ fontSize: '19px', letterSpacing: '-0.03em', wordBreak: 'keep-all' }}
        >
          {tile.title}
        </h3>
        <p className="mt-2 font-mono text-caption text-fg-subtle">{tile.tags.join(' · ')}</p>
      </div>
    </>
  )
}

export function HomeServices() {
  const navigate = useNavigate()
  const [roleOpen, setRoleOpen] = useState(false)

  const goRole = (track: (typeof ROLE_OPTIONS)[number]['track']) => {
    setRoleOpen(false)
    navigate(`/practice/${track}`)
  }

  return (
    <section id="services" className="bg-surface">
      <div className="mx-auto max-w-content px-6 py-16 lg:px-12 lg:py-20">
        <Reveal>
          <div className="flex flex-wrap items-baseline justify-between gap-3">
            <h2
              className="font-sans font-bold text-fg"
              style={{
                fontSize: 'clamp(24px, 2.6vw, 34px)',
                letterSpacing: '-0.03em',
                wordBreak: 'keep-all',
              }}
            >
              세 가지 면접이 준비돼 있습니다
            </h2>
            <span className="font-mono text-caption text-fg-subtle">3 / 3 available</span>
          </div>
        </Reveal>

        <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {services.map((s, i) => (
            <Reveal as="li" key={s.title} delayMs={i * 60}>
              {'to' in s ? (
                <Link to={s.to} className={tileClass}>
                  <TileInner tile={s} />
                </Link>
              ) : (
                <button type="button" onClick={() => setRoleOpen(true)} className={tileClass}>
                  <TileInner tile={s} />
                </button>
              )}
            </Reveal>
          ))}
        </ul>
      </div>

      <Modal open={roleOpen} onClose={() => setRoleOpen(false)} title="직무 기술 면접">
        <p className="text-body text-fg-muted">
          연습할 직무를 선택하면 해당 분야 질문이 무작위로 출제됩니다.
        </p>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          {ROLE_OPTIONS.map((opt) => (
            <button
              key={opt.track}
              type="button"
              onClick={() => goRole(opt.track)}
              className="flex flex-col gap-1 rounded-lg border border-border bg-surface-raised px-4 py-4 text-left transition-colors hover:border-border-strong hover:bg-surface"
            >
              <span className="text-button text-fg">{opt.label}</span>
              <span className="text-caption text-fg-muted">{opt.desc}</span>
            </button>
          ))}
        </div>
      </Modal>
    </section>
  )
}
