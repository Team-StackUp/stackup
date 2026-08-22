import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEventStream } from '@/shared/hooks'
import type { StreamConnectionStatus } from '@/shared/hooks'
import { getFeedback } from '../api/feedbackApi'
import { feedbackKeys, isFeedbackPending } from './useFeedback'

// AI 피드백 생성 진행 문구(FEEDBACK_PROGRESS, 휘발성) — 스켈레톤 캡션에 표시.
export type FeedbackProgress = {
  message: string
  completed: number | null
  total: number | null
}

// AI 피드백 생성 실패 알림(ERROR, scope=FEEDBACK — event-stream.md §3.6 SessionErrorNotice).
// 수신 즉시 폴링 예산(≈2분) 소진을 기다리지 않고 재생성 복구 UI 로 전환하기 위한 상태.
export type FeedbackFailure = {
  message: string
  retriable: boolean | null
}

// RealTime SSE data 봉투: { data: <payload>, traceId } (useWorkspaceAnalysisStream 과 동일).
type StreamData<T> = { data?: T; traceId?: string | null }

// 피드백 조회 + 세션 채널 SSE(FEEDBACK_READY·FEEDBACK_PROGRESS) 구독.
// - 준비 완료 이벤트 수신 즉시 재조회 — 3s 폴링 간격을 기다리지 않는다.
// - 기존 3s×40 재시도는 백스톱으로 축소: 스트림이 살아 있으면 15s 간격(이벤트가 주 신호),
//   죽어 있으면 기존대로 3s (api-conventions §9 — SSE 우선, 끊기면 폴링).
//   어느 모드든 총 대기 예산은 ≈2분 — 소진 시 페이지가 재생성 복구 UI 를 연다.
// - getToken 은 호출부(페이지)가 주입한다 — 세션 stream token API 는 interview 슬라이스
//   소유라 feedback 이 직접 import 할 수 없다 (FSD 동일 레이어 금지).
export function useFeedbackLive(sessionId: number, getToken: () => Promise<string | null>) {
  const queryClient = useQueryClient()

  // retry 콜백은 비동기 시점에 실행되므로 ref 로 최신 스트림 상태를 읽는다.
  const statusRef = useRef<StreamConnectionStatus>('connecting')

  const query = useQuery({
    queryKey: feedbackKeys.detail(sessionId),
    queryFn: () => getFeedback(sessionId),
    retry: (count, err) =>
      isFeedbackPending(err) && count < (statusRef.current === 'open' ? 8 : 40),
    retryDelay: () => (statusRef.current === 'open' ? 15_000 : 3_000),
  })

  const onReady = useCallback(() => {
    // 데이터가 이미 도착한 뒤 늦게 온 이벤트는 무시 — reset 하면 표시 중인 리포트가
    // 스켈레톤으로 되돌아간다 (스트림 close 는 다음 커밋에서야 일어나는 좁은 레이스 창).
    if (queryClient.getQueryData(feedbackKeys.detail(sessionId)) !== undefined) return
    // 재시도 대기 중인 쿼리를 리셋해 즉시 다시 조회한다 (useRegenerateFeedback 과 동일 패턴).
    void queryClient.resetQueries({ queryKey: feedbackKeys.detail(sessionId) })
  }, [queryClient, sessionId])

  const [progress, setProgress] = useState<FeedbackProgress | null>(null)
  const onProgress = useCallback((raw: unknown) => {
    const data = (raw as StreamData<{ message?: string; completed?: number; total?: number }> | null)
      ?.data
    if (!data || typeof data.message !== 'string') return
    setProgress({
      message: data.message,
      completed: typeof data.completed === 'number' ? data.completed : null,
      total: typeof data.total === 'number' ? data.total : null,
    })
  }, [])

  // 생성 실패 신호. 세션 채널 ERROR 는 꼬리질문(FOLLOWUP) 실패도 흐르므로 scope 로 거른다.
  // 데이터가 이미 도착했으면 늦은 실패 이벤트는 소비부(!data 가드)에서 무시된다.
  const [failure, setFailure] = useState<FeedbackFailure | null>(null)
  const onError = useCallback((raw: unknown) => {
    const data = (
      raw as StreamData<{ scope?: string; message?: string; retriable?: boolean }> | null
    )?.data
    if (!data || data.scope !== 'FEEDBACK' || typeof data.message !== 'string') return
    setFailure({
      message: data.message,
      retriable: typeof data.retriable === 'boolean' ? data.retriable : null,
    })
  }, [])
  // 재생성 요청 시 페이지가 호출 — 대기(스켈레톤) 상태로 되돌린다.
  const resetFailure = useCallback(() => setFailure(null), [])

  // 피드백이 도착하면 스트림을 닫는다 (path: null).
  const streamStatus = useEventStream({
    path: query.data ? null : `/realtime/stream/sessions/${sessionId}`,
    getToken,
    handlers: { FEEDBACK_READY: onReady, FEEDBACK_PROGRESS: onProgress, ERROR: onError },
  })

  useEffect(() => {
    statusRef.current = streamStatus
  }, [streamStatus])

  return { ...query, streamStatus, progress, failure, resetFailure }
}
