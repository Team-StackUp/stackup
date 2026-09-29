export const CAREER_LEVELS = [
  { value: 'NEW', label: '신입' },
  { value: 'EXPERIENCED', label: '경력' },
  { value: 'INTERN', label: '인턴·체험형' },
  { value: 'CAREER_CHANGE', label: '직무 전환' },
] as const

export type CareerLevel = (typeof CAREER_LEVELS)[number]['value']

export const CAREER_LEVEL_LABEL: Record<CareerLevel, string> = Object.fromEntries(
  CAREER_LEVELS.map((o) => [o.value, o.label] as const),
) as Record<CareerLevel, string>
