import { useEffect, useState } from 'react'

import { fetchJobProfile, saveJobProfile, type JobProfile } from '../api/jobProfileApi'
import { IndustryField } from './IndustryField'
import { CAREER_LEVELS, type CareerLevel } from '@/domain/session/model/careerLevel'
import { JobCategorySelector } from '@/features/interview/ui/setup/JobCategorySelector'
import { Button } from '@/shared/ui/Button'
import type { JobCategory } from '@/domain/session'

/**
 * 취업 프로필 — 희망 직군·산업·경력.
 *
 * 면접을 만들 때마다 직군을 다시 고르게 했고, 직군만으로는 질문 맥락이 얇았다. 여기서 한 번
 * 채우면 면접 생성 화면이 기본값으로 채우고, 사용자는 그 자리에서 바꿀 수 있다.
 */
export function JobProfileForm({ onSaved }: { onSaved?: (profile: JobProfile) => void }) {
  const [categories, setCategories] = useState<JobCategory[]>([])
  const [industry, setIndustry] = useState('')
  const [career, setCareer] = useState<CareerLevel | ''>('')
  const [status, setStatus] = useState<'loading' | 'idle' | 'saving' | 'saved' | 'error'>(
    'loading',
  )

  useEffect(() => {
    let alive = true
    fetchJobProfile()
      .then((p) => {
        if (!alive) return
        setCategories(p.desiredJobCategories)
        setIndustry(p.desiredIndustry ?? '')
        setCareer(p.careerLevel ?? '')
        setStatus('idle')
      })
      .catch(() => alive && setStatus('error'))
    return () => {
      alive = false
    }
  }, [])

  const toggle = (value: JobCategory) =>
    setCategories((prev) =>
      prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value],
    )

  const save = async () => {
    setStatus('saving')
    try {
      const saved = await saveJobProfile({
        desiredJobCategories: categories,
        // 빈 문자열은 "지우기" 다(null 은 "그대로 두기").
        desiredIndustry: industry,
        careerLevel: career === '' ? undefined : career,
      })
      setStatus('saved')
      onSaved?.(saved)
    } catch {
      setStatus('error')
    }
  }

  if (status === 'loading') {
    return <p className="text-caption text-fg-subtle">불러오는 중…</p>
  }

  return (
    <div className="space-y-5">
      <div className="space-y-2">
        <h3 className="text-[13px] font-semibold text-fg-muted">희망 직군</h3>
        <JobCategorySelector value={categories} onToggle={toggle} />
      </div>

      <IndustryField value={industry} onChange={setIndustry} />

      <div className="space-y-1.5">
        <span className="block text-[13px] font-semibold text-fg-muted">경력 수준</span>
        <div role="group" aria-label="경력 수준" className="flex flex-wrap gap-2">
          {CAREER_LEVELS.map((o) => (
            <button
              key={o.value}
              type="button"
              aria-pressed={career === o.value}
              onClick={() => setCareer(career === o.value ? '' : o.value)}
              className={`rounded-full border px-3 py-1.5 text-caption transition ${
                career === o.value
                  ? 'border-brand bg-brand-subtle text-brand-strong'
                  : 'border-border text-fg-muted hover:border-border-strong'
              }`}
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex items-center gap-3">
        <Button onClick={save} disabled={status === 'saving'}>
          {status === 'saving' ? '저장 중…' : '저장'}
        </Button>
        {status === 'saved' && <span className="text-caption text-fg-muted">저장했어요.</span>}
        {status === 'error' && (
          <span className="text-caption text-danger">저장하지 못했어요. 잠시 후 다시 시도해 주세요.</span>
        )}
      </div>
    </div>
  )
}
