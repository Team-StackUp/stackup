import type { PracticeTrack, QuestionBank } from '@/domain/practice'

// 서버 비용을 피하기 위해 질문 은행은 public/data 의 정적 JSON 으로 제공된다.
const TRACK_FILE: Record<PracticeTrack, string> = {
  frontend: '/data/frontend-interview-questions.json',
  backend: '/data/backend-interview-questions.json',
  cs: '/data/cs_interview_questions.json',
}

export async function loadQuestionBank(track: PracticeTrack): Promise<QuestionBank> {
  const res = await fetch(TRACK_FILE[track])
  if (!res.ok) {
    throw new Error(`질문 데이터를 불러오지 못했습니다 (${res.status})`)
  }
  return (await res.json()) as QuestionBank
}
