import { SiteNav } from '@/widgets/site-nav'
import { HomeHero } from '@/widgets/home-hero'
import { HomeServices } from '@/widgets/home-services'
import { HomeQuote } from '@/widgets/home-quote'
import { HomeFaq } from '@/widgets/home-faq'
import { HomeCta } from '@/widgets/home-cta'
import { SiteFooter } from '@/widgets/site-footer'

export default function HomePage() {
  return (
    <div className="min-h-svh bg-bg text-fg">
      <SiteNav />
      <main>
        <HomeHero />
        <HomeServices />
        <HomeQuote />
        <HomeFaq />
        <HomeCta />
      </main>
      <SiteFooter />
    </div>
  )
}