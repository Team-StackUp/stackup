import { memo } from 'react'
import { useWebcamPreview } from '../../lib/media/useWebcamPreview'

function CameraOffIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M2.1 3.51 1 4.62l4.51 4.51A1 1 0 0 0 5 10v8a2 2 0 0 0 2 2h10c.21 0 .41-.03.6-.09l1.78 1.78 1.11-1.11L2.1 3.51ZM17 8.5V6a2 2 0 0 0-2-2H8.83l9.34 9.34A1 1 0 0 0 19 13l3 2V7l-3 2a1 1 0 0 0-2-.5Z" />
    </svg>
  )
}

const PLACEHOLDER: Record<string, string> = {
  idle: '카메라 꺼짐',
  requesting: '연결 중…',
  denied: '권한 거부됨',
  unsupported: '미지원 기기',
}

// 면접 스테이지에 떠 있는 본인 카메라 미리보기 카드. 위치는 호출부(InterviewStage)가 결정.
export const WebcamSelfView = memo(function WebcamSelfView() {
  const { videoRef, state, start, stop } = useWebcamPreview()
  const live = state === 'live'

  return (
    <div className="w-36 overflow-hidden rounded-xl border border-surface-raised/50 bg-sage-900/80 shadow-lg backdrop-blur-md sm:w-44">
      <div className="relative aspect-video">
        <video
          ref={videoRef}
          autoPlay
          muted
          playsInline
          aria-label="내 카메라 미리보기"
          className={['h-full w-full -scale-x-100 object-cover', live ? '' : 'hidden'].join(' ')}
        />
        {!live && (
          <div className="flex h-full w-full flex-col items-center justify-center gap-1 text-white/70">
            <CameraOffIcon />
            <span className="text-[11px]">{PLACEHOLDER[state]}</span>
          </div>
        )}
      </div>
      <button
        type="button"
        onClick={live ? stop : start}
        disabled={state === 'requesting' || state === 'unsupported'}
        className="w-full bg-black/30 py-1 text-[11px] font-medium text-white transition-colors hover:bg-black/40 disabled:opacity-60"
      >
        {live ? '카메라 끄기' : '카메라 켜기'}
      </button>
    </div>
  )
})
