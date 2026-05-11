export type SectionId =
  | 'overview'
  | 'colors'
  | 'typography'
  | 'radius'
  | 'shadow'
  | 'spacing'
  | 'motion'
  | 'z-index'
  | 'container'

export const NAV: { id: SectionId; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'colors', label: 'Colors' },
  { id: 'typography', label: 'Typography' },
  { id: 'radius', label: 'Radius' },
  { id: 'shadow', label: 'Shadow' },
  { id: 'spacing', label: 'Spacing' },
  { id: 'motion', label: 'Motion' },
  { id: 'z-index', label: 'Z-Index' },
  { id: 'container', label: 'Container' },
]
