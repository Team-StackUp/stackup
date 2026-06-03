import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
import { RadioCardGroup } from '@/shared/ui/RadioCardGroup'

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
      <div>
        <h1 className="text-h4 text-fg">직무 기술 면접</h1>
        <p className="mt-2 text-body text-fg-muted">
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
