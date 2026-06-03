// 면접 질문 카테고리 ENUM → 한국어 라벨. 라이브 스테이지와 채팅 버블이 공유한다.
const CATEGORY_LABEL: Record<string, string> = {
  CS_FUNDAMENTAL: 'CS 기초',
  PROJECT_DEEP_DIVE: '프로젝트 심화',
  TECH_CHOICE: '기술 선택',
  BEHAVIORAL: '인성·행동',
}

export function categoryLabel(category?: string | null): string | null {
  if (!category) return null
  return CATEGORY_LABEL[category] ?? category
}
