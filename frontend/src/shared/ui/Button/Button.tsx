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

const variantClass: Record<Variant, string> = {
  primary: 'bg-primary text-fg-on-primary hover:bg-primary-hover',
  secondary: 'bg-surface-raised text-fg border border-border hover:bg-surface',
  ghost: 'text-fg hover:bg-surface',
  danger: 'bg-danger text-white hover:bg-danger-700',
}

const sizeClass: Record<Size, string> = {
  sm: 'text-button px-3 py-1.5',
  md: 'text-button px-4 py-2',
  lg: 'text-body px-5 py-2.5',
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
      className={`inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${variantClass[variant]} ${sizeClass[size]} ${className}`}
      {...rest}
    >
      {loading && <Spinner size="sm" />}
      {children}
    </button>
  )
}
