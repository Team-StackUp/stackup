// 면접 질문 카테고리 ENUM → 한국어 라벨. 라이브 스테이지와 채팅 버블이 공유한다.
const CATEGORY_LABEL: Record<string, string> = {
  SELF_INTRODUCTION: '자기소개',
  CS_FUNDAMENTAL: 'CS 기초',
  PROJECT_DEEP_DIVE: '프로젝트 심화',
  TECH_CHOICE: '기술 선택',
  BEHAVIORAL: '인성·행동',
  // 비개발 직군용. CS 기초·기술 선택이 개발 전용이라 그 자리를 대신한다.
  DOMAIN_KNOWLEDGE: '직무 지식',
}

export function categoryLabel(category?: string | null): string | null {
  if (!category) return null
  return CATEGORY_LABEL[category] ?? category
}
