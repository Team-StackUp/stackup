import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { SiteNav } from '@/widgets/site-nav'
import { HomeHero } from '@/widgets/home-hero'
import { HomeSteps } from '@/widgets/home-steps'
import { HomeFeatures } from '@/widgets/home-features'
import { HomeServices } from '@/widgets/home-services'
import { HomeFaq } from '@/widgets/home-faq'
import { SiteFooter } from '@/widgets/site-footer'

export default function HomePage() {
  const { hash } = useLocation()

  // 다른 페이지 또는 풀 리로드로 진입할 때 #section 으로 스크롤.
  // (라우터는 hash 스크롤을 보장하지 않고, 풀 리로드 시 엘리먼트가
  // 아직 마운트 전이라 브라우저 기본 스크롤이 빗나감)
  useEffect(() => {
    const id = hash.replace('#', '')
    if (!id) {
      window.scrollTo({ top: 0 })
      return
    }
    const raf = requestAnimationFrame(() => {
      document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
    })
    return () => cancelAnimationFrame(raf)
  }, [hash])

  return (
    <div className="min-h-svh bg-surface-raised text-fg">
      <SiteNav />
      <main>
        <HomeHero />
        <HomeSteps />
        <HomeFeatures />
        <HomeServices />
        <HomeFaq />
      </main>
      <SiteFooter cta />
    </div>
  )
}
