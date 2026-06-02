import type { Ref, TextareaHTMLAttributes } from 'react'

type Base = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'onChange' | 'value'>

export type TextAreaProps = Base & {
  value: string
  onChange: (value: string) => void
  ref?: Ref<HTMLTextAreaElement>
}

export function TextArea({ value, onChange, className = '', rows = 1, ref, ...rest }: TextAreaProps) {
  return (
    <textarea
      ref={ref}
      value={value}
      rows={rows}
      onChange={(e) => onChange(e.target.value)}
      className={`max-h-40 min-h-10 flex-1 resize-none rounded-md border border-border bg-surface-raised px-3 py-2 text-body text-fg placeholder:text-fg-muted focus:border-border-strong focus:outline-none disabled:opacity-50 ${className}`}
      {...rest}
    />
  )
}
