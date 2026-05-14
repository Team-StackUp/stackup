// PR 2 (feature/home-layout) 단계의 HomePage.tsx
import { SiteNav } from '@/widgets/site-nav'
import { SiteFooter } from '@/widgets/site-footer'
// 아직 PR 3에서 만들 예정이므로 주석 처리!
// import { HomeHero } from '@/widgets/home-hero' 

export default function HomePage() {
  return (
    <div className="min-h-svh bg-bg text-fg">
      {/* 글로벌 레이아웃은 추후 분리 예정*/}
      <SiteNav />
      <main>
        {/* TODO: 다음 PR에서 위젯들 추가 예정 */}
      </main>
      <SiteFooter />
    </div>
  )
}