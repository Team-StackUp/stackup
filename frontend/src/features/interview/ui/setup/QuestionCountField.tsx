import { Stepper } from '@/shared/ui/Stepper'

export function QuestionCountField({
  value,
  onChange,
}: {
  value: number
  onChange: (value: number) => void
}) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-body text-fg">질문 수</span>
      <Stepper ariaLabel="질문 수" value={value} onChange={onChange} min={2} max={30} />
    </div>
  )
}
