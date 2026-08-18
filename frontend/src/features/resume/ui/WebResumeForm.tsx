import { useId, useState } from 'react'
import { isApiError } from '@/shared/api'
import { Button } from '@/shared/ui/Button'
import { useRegisterWebResume } from '../model/useResumes'

// 서버(WebResumeUrlValidator)가 최종 판정하지만, 왕복 전에 명백한 실수는 여기서 잡는다.
function localReason(raw: string): string | null {
  const value = raw.trim()
  if (!value) return 'URL을 입력해 주세요.'
  let parsed: URL
  try {
    parsed = new URL(value)
  } catch {
    return 'http:// 또는 https:// 로 시작하는 전체 주소를 입력해 주세요.'
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return 'http, https 주소만 등록할 수 있습니다.'
  }
  return null
}

export function WebResumeForm() {
  const inputId = useId()
  const errorId = useId()
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const register = useRegisterWebResume()

  const submit = () => {
    const reason = localReason(value)
    if (reason) {
      setError(reason)
      return
    }
    setError(null)
    register.mutate(value.trim(), {
      onSuccess: () => setValue(''),
      onError: (e) =>
        setError(isApiError(e) ? e.message : '링크 등록에 실패했습니다.'),
    })
  }

  return (
    <form
      className="flex flex-col gap-2"
      // 브라우저 기본 검증 버블을 끈다 — type="url" 에 맡기면 제출이 막혀 우리 한국어 안내가
      // 뜨지 않고, 브라우저별로 문구·동작이 갈린다. 검증 주체는 localReason 하나로 둔다.
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
    >
      <label htmlFor={inputId} className="text-caption font-medium text-fg-muted">
        포트폴리오·블로그 링크
      </label>
      <div className="flex flex-col gap-2 sm:flex-row">
        <input
          id={inputId}
          type="url"
          inputMode="url"
          value={value}
          placeholder="https://my-portfolio.dev"
          disabled={register.isPending}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          onChange={(e) => {
            setValue(e.target.value)
            if (error) setError(null)
          }}
          className="min-w-0 flex-1 rounded-lg border border-border bg-surface-raised px-3 py-2.5 text-body text-fg placeholder:text-fg-subtle focus-visible:border-primary focus-visible:outline-none disabled:opacity-60 aria-[invalid=true]:border-danger-700"
        />
        <Button type="submit" loading={register.isPending} className="sm:w-auto">
          링크 등록
        </Button>
      </div>
      {error ? (
        <p id={errorId} role="alert" className="text-caption text-danger-700">
          {error}
        </p>
      ) : (
        <p className="text-caption text-fg-subtle">
          공개된 페이지만 등록할 수 있어요. 본문을 읽어 이력서와 같은 방식으로 분석합니다.
        </p>
      )}
    </form>
  )
}
