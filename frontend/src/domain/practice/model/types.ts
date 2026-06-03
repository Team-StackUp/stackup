// 정적(서버리스) 연습 면접 도메인 모델.
// public/data/*.json 의 스키마(subjects → categories → questions)를 그대로 반영한다.

export type PracticeTrack = 'frontend' | 'backend' | 'cs'

export interface RawQuestion {
  id: string
  question: string
  answer: string
}

export interface RawCategory {
  id: string
  name: string
  questions: RawQuestion[]
}

export interface RawSubject {
  id: string
  name: string
  categories: RawCategory[]
}

export interface QuestionBank {
  version: string
  title: string
  description: string
  subjects: RawSubject[]
}

// 면접에서 출제되는 단일 질문. 어느 과목/카테고리에서 왔는지 함께 보존한다.
export interface PracticeQuestion {
  id: string
  question: string
  answer: string
  subject: string
  category: string
}
