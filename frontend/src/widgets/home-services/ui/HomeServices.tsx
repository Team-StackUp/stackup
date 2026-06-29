import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Modal } from '@/shared/ui'

type ServiceCard = {
  tag: string
  title: string
  image: string
} & ({ to: string } | { modal: 'role' })

const services: ServiceCard[] = [
  {
    tag: 'Pro Plan',
    title: '이력서 심층 면접\nExperience Interview',
    image: '/second-section-frontend-interview.png',
    to: '/sessions/new',
  },
  {
    tag: 'Free Plan',
    title: '직무 기술 면접\nRole-based Interview',
    image: '/second-section-backend-interview.avif',
    // 직무는 프론트/백엔드를 모달에서 고른 뒤 해당 연습으로 이동.
    modal: 'role',
  },
  {
    tag: 'Free Plan',
    title: 'CS 전공 지식 면접\nCS Core Interview',
    image: '/second-section-cs-interview.avif',
    to: '/practice/cs',
  },
]

const ROLE_OPTIONS = [
  { track: 'frontend', label: '프론트엔드', desc: 'HTML·CSS·JS·React·브라우저' },
  { track: 'backend', label: '백엔드', desc: 'OS·네트워크·DB·자바·스프링' },
] as const

const cardClass =
  'group relative block rounded-2xl overflow-hidden aspect-[3/4] bg-sage-800 text-left'

function CardInner({ card }: { card: ServiceCard }) {
  return (
    <>
      <img
        src={card.image}
        alt={card.title}
        loading="lazy"
        decoding="async"
        className="absolute inset-0 w-full h-full object-cover transition-transform duration-slow ease-standard group-hover:scale-[1.04]"
      />
      <div
        aria-hidden
        className="absolute inset-0"
        style={{
          background:
            'linear-gradient(180deg, rgba(20,26,17,0.05) 0%, rgba(20,26,17,0.05) 55%, rgba(20,26,17,0.75) 100%)',
        }}
      />

      <span className="absolute top-4 left-4 px-3 py-1 rounded-pill bg-[#dbe2ec] text-sage-900 text-caption font-medium">
        {card.tag}
      </span>

      <div className="absolute inset-x-5 bottom-5 flex items-end justify-between gap-4 text-white">
        <h3 className="whitespace-pre-line font-heading font-semibold text-[26px] lg:text-[28px] leading-tight text-white">
          {card.title}
        </h3>
        <span
          aria-hidden
          className="shrink-0 inline-flex items-center justify-center w-9 h-9 rounded-pill bg-white/12 backdrop-blur-sm border border-white/20 transition-transform duration-fast group-hover:translate-x-1"
        >
          →
        </span>
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
    <section id="services" className="bg-bg">
      <div className="mx-auto max-w-content px-6 lg:px-12 pt-16 pb-24 lg:pt-24 lg:pb-32">
        <h2
          className="font-heading font-extrabold uppercase text-sage-900 leading-[0.95] tracking-tight"
          style={{ fontSize: 'clamp(40px, 5vw, 64px)' }}
        >
          Our Services
        </h2>

        <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {services.map((s) =>
            'to' in s ? (
              <Link key={s.title} to={s.to} className={cardClass}>
                <CardInner card={s} />
              </Link>
            ) : (
              <button
                key={s.title}
                type="button"
                onClick={() => setRoleOpen(true)}
                className={cardClass}
              >
                <CardInner card={s} />
              </button>
            ),
          )}
        </div>
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
