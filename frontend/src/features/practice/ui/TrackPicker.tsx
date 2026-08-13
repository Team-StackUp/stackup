import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import { RadioCardGroup } from '@/shared/ui/RadioCardGroup'
import { Eyebrow, Heading } from '@/shared/ui'

type RoleTrack = 'frontend' | 'backend'

const OPTIONS = [
  { value: 'frontend' as const, label: '프론트엔드', description: 'HTML·CSS·JS·React·브라우저' },
  { value: 'backend' as const, label: '백엔드', description: 'OS·네트워크·DB·자바·스프링' },
]

export function TrackPicker() {
  const navigate = useNavigate()
  const [track, setTrack] = useState<RoleTrack | null>(null)

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-6 py-16">
      <div className="border-b border-border pb-6">
        <Eyebrow>연습 모드</Eyebrow>
        <Heading level="page" as="h1" className="mt-3">
          직무 기술 면접
        </Heading>
        <p className="mt-3 text-body font-normal text-fg-muted" style={{ wordBreak: 'keep-all' }}>
          연습할 직무를 선택하면 해당 분야 질문이 무작위로 출제됩니다.
        </p>
      </div>

      <RadioCardGroup
        options={OPTIONS}
        value={track}
        onChange={setTrack}
        ariaLabel="직무 선택"
      />

      <div className="flex justify-end">
        <Button disabled={!track} onClick={() => track && navigate(`/practice/${track}`)}>
          면접 시작
        </Button>
      </div>
    </div>
  )
}
