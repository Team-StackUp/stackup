import { Link } from 'react-router-dom'

type ServiceCard = {
  tag: string
  title: string
  image: string
  to: string
}

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
    to: '/practice/role',
  },
  {
    tag: 'Free Plan',
    title: 'CS 전공 지식 면접\nCS Core Interview',
    image: '/second-section-cs-interview.avif',
    to: '/practice/cs',
  },
]

export function HomeServices() {
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
          {services.map((s) => (
            <Link
              key={s.title}
              to={s.to}
              className="group relative block rounded-2xl overflow-hidden aspect-[3/4] bg-sage-800"
            >
              <img
                src={s.image}
                alt={s.title}
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

              <span className="absolute top-4 left-4 px-3 py-1 rounded-pill bg-[#e6dfd4] text-sage-900 text-caption font-medium">
                {s.tag}
              </span>

              <div className="absolute inset-x-5 bottom-5 flex items-end justify-between gap-4 text-white">
                <h3 className="whitespace-pre-line font-heading font-semibold text-[26px] lg:text-[28px] leading-tight text-white">
                  {s.title}
                </h3>
                <span
                  aria-hidden
                  className="shrink-0 inline-flex items-center justify-center w-9 h-9 rounded-pill bg-white/12 backdrop-blur-sm border border-white/20 transition-transform duration-fast group-hover:translate-x-1"
                >
                  →
                </span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  )
}
