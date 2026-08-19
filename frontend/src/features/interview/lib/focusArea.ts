// 세션의 집중 영역(SessionFocusArea) 표시명. 서버가 축을 늘려도 화면이 깨지지 않게 원본을 폴백한다.
const LABEL: Record<string, string> = {
  TECHNICAL: '기술 정확도',
  LOGIC: '논리력',
  COMMUNICATION: '전달력',
}

export function focusAreaLabel(area: string): string {
  return LABEL[area] ?? area
}
