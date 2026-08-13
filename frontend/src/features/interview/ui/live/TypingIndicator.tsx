export function TypingIndicator() {
  return (
    <div className="flex justify-start" role="status" aria-label="질문 생성 중">
      <div className="flex gap-1 rounded-xl rounded-tl-sm border border-border bg-surface-raised px-4 py-4">
        <span className="h-2 w-2 animate-bounce rounded-full bg-fg-muted [animation-delay:-0.3s]" />
        <span className="h-2 w-2 animate-bounce rounded-full bg-fg-muted [animation-delay:-0.15s]" />
        <span className="h-2 w-2 animate-bounce rounded-full bg-fg-muted" />
      </div>
    </div>
  )
}
