import { memo, useState } from 'react'

export type InterviewerState = 'idle' | 'thinking' | 'asking' | 'speaking'

// public/interviewer.png 가 있으면 사용, 없으면 onError 로 사람 아이콘 폴백.
const SRC = '/interviewer.png'

function FallbackFace() {
  return (
    <span role="img" aria-label="면접관" className="flex h-full w-full items-center justify-center">
      <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden className="h-1/2 w-1/2">
        <path d="M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0 2c-4.42 0-8 2.69-8 6v1h16v-1c0-3.31-3.58-6-8-6Z" />
      </svg>
    </span>
  )
}

// 라이브 면접 스테이지 상단에서 "사람이 묻는" 존재감을 주는 면접관 아바타.
// 캡션(면접관/카테고리)은 아래 질문 카드가 이미 보여주므로 여기서는 시각 요소만 담당한다.
export const InterviewerAvatar = memo(function InterviewerAvatar({ state }: { state: InterviewerState }) {
  const [imgFailed, setImgFailed] = useState(false)

  const ringColor =
    state === 'speaking' ? 'ring-primary' : state === 'asking' ? 'ring-primary/60' : 'ring-surface-raised/50'

  return (
    <div className="relative flex items-center justify-center">
      {state === 'thinking' && (
        <span
          aria-hidden
          className="absolute -inset-1 animate-ping rounded-full border-2 border-sage-400/40"
        />
      )}
      {/* 말하는 동안 음성 파동(sonar) 링 — 2겹을 시차 재생해 '소리내는' 느낌. */}
      {state === 'speaking' &&
        [0, 1].map((i) => (
          <span
            key={i}
            aria-hidden
            className="absolute -inset-1 animate-ping rounded-full border-2 border-primary/50"
            style={{ animationDelay: `${i * 500}ms` }}
          />
        ))}
      <div
        className={[
          'relative flex h-20 w-20 items-center justify-center overflow-hidden rounded-full',
          'bg-sage-800 text-white shadow-lg ring-4 transition-[box-shadow,color] duration-slow sm:h-24 sm:w-24',
          ringColor,
        ].join(' ')}
      >
        {imgFailed ? (
          <FallbackFace />
        ) : (
          <img
            src={SRC}
            alt="면접관"
            className="h-full w-full object-cover"
            onError={() => setImgFailed(true)}
          />
        )}
      </div>
    </div>
  )
})
