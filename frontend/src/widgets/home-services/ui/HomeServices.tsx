import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Modal, Reveal } from '@/shared/ui'

type ServiceCard = {
  plan: 'Pro' | 'Free'
  title: string
  desc: string
  tags: string[]
} & ({ to: string } | { modal: 'role' })

const services: ServiceCard[] = [
  {
    plan: 'Pro',
    title: '이력서 심층 면접',
    desc: '내 자료에서 나온 질문으로 깊게.',
    tags: ['이력서', '자소서', 'GitHub'],
    to: '/sessions/new',
  },
  {
    plan: 'Free',
    title: '직무 기술 면접',
    desc: '직무 빈출 질문으로 가볍게.',
    tags: ['프론트엔드', '백엔드'],
    modal: 'role',
  },
  {
    plan: 'Free',
    title: 'CS 전공 지식 면접',
    desc: '전공 개념만 빠르게 점검.',
    tags: ['OS', '네트워크', 'DB'],
    to: '/practice/cs',
  },
]

const ROLE_OPTIONS = [
  { track: 'frontend', label: '프론트엔드', desc: 'HTML·CSS·JS·React·브라우저' },
  { track: 'backend', label: '백엔드', desc: 'OS·네트워크·DB·자바·스프링' },
] as const

const cardClass =
  'group flex h-full w-full flex-col rounded-2xl border border-border bg-surface-raised p-6 text-left transition-colors duration-fast hover:border-border-strong lg:p-7'

function CardInner({ card }: { card: ServiceCard }) {
  return (
    <>
      <div className="flex items-center justify-between gap-3">
        <span
          className={`rounded-pill px-2.5 py-1 text-caption font-semibold ${
            card.plan === 'Pro'
              ? 'bg-primary-100 text-primary-fg'
              : 'bg-surface text-fg-muted'
          }`}
        >
          {card.plan}
        </span>
        <span
          aria-hidden
          className="text-fg-subtle transition-transform duration-fast group-hover:translate-x-1"
        >
          →
        </span>
      </div>

      <h3 className="mt-5 font-sans text-h6 text-fg">{card.title}</h3>
      <p
        className="mt-2.5 text-body font-normal leading-relaxed text-fg-muted"
        style={{ wordBreak: 'keep-all' }}
      >
        {card.desc}
      </p>

      <ul className="mt-auto flex flex-wrap gap-1.5 pt-6">
        {card.tags.map((t) => (
          <li
            key={t}
            className="rounded-pill border border-border px-2.5 py-1 text-caption text-fg-subtle"
          >
            {t}
          </li>
        ))}
      </ul>
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
      <div className="mx-auto max-w-content px-6 py-24 lg:px-12 lg:py-32">
        <Reveal>
          <p className="font-mono text-caption tracking-tight text-fg-subtle">PICK ONE</p>
          <h2
            className="mt-4 font-sans font-bold text-fg"
            style={{
              fontSize: 'clamp(28px, 3.4vw, 44px)',
              lineHeight: 1.25,
              letterSpacing: '-0.03em',
              wordBreak: 'keep-all',
            }}
          >
            골라서 바로 시작
          </h2>
        </Reveal>

        <ul className="mt-14 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {services.map((s, i) => (
            <Reveal as="li" key={s.title} delayMs={i * 80}>
              {'to' in s ? (
                <Link to={s.to} className={cardClass}>
                  <CardInner card={s} />
                </Link>
              ) : (
                <button type="button" onClick={() => setRoleOpen(true)} className={cardClass}>
                  <CardInner card={s} />
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
