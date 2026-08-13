import type { ButtonHTMLAttributes, ReactNode, Ref } from 'react'
import { Spinner } from '../Spinner'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

export type ButtonProps = {
  variant?: Variant
  size?: Size
  loading?: boolean
  children: ReactNode
  ref?: Ref<HTMLButtonElement>
} & Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'>

// 랜딩 CTA 와 같은 형태 — 굵은 라벨, 넉넉한 세로 패딩, 그림자 없음.
// 깊이는 그림자가 아니라 보더 한 줄로만 준다(`Panel` 과 같은 규칙).
const variantClass: Record<Variant, string> = {
  primary: 'bg-primary text-fg-on-primary hover:bg-primary-hover',
  secondary:
    'bg-surface-raised text-fg-strong border border-border-strong hover:bg-surface',
  ghost: 'text-fg-muted hover:bg-surface hover:text-fg-strong',
  danger: 'bg-danger text-white hover:bg-danger-700',
}

// 큰 버튼일수록 라운드도 커진다 — 랜딩 히어로 CTA(rounded-xl)와 맞춘다.
const sizeClass: Record<Size, string> = {
  sm: 'rounded-lg text-button px-3 py-1.5',
  md: 'rounded-lg text-button px-4 py-2.5',
  lg: 'rounded-xl text-body px-6 py-3.5',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  className = '',
  children,
  ref,
  ...rest
}: ButtonProps) {
  return (
    <button
      ref={ref}
      disabled={(disabled ?? false) || loading}
      className={`inline-flex items-center justify-center gap-2 font-semibold transition-colors duration-fast disabled:cursor-not-allowed disabled:opacity-50 ${variantClass[variant]} ${sizeClass[size]} ${className}`}
      {...rest}
    >
      {loading && <Spinner size="sm" />}
      {children}
    </button>
  )
}
