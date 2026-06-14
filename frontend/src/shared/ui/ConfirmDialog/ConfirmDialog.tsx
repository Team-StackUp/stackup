import type { ReactNode } from 'react'
import { Modal } from '../Modal'
import { Button } from '../Button'

export type ConfirmDialogProps = {
  open: boolean
  title: ReactNode
  description?: ReactNode
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

// 파괴적/되돌릴 수 없는 액션 전 확인을 받는 공용 다이얼로그.
// Modal(ESC·포커스 복원·스크롤 락 내장) 위에 표준 취소/확인 푸터를 얹는다.
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = '확인',
  cancelLabel = '취소',
  danger = false,
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Modal
      open={open}
      onClose={loading ? () => {} : onCancel}
      title={title}
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onCancel} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button
            variant={danger ? 'danger' : 'primary'}
            onClick={onConfirm}
            loading={loading}
          >
            {confirmLabel}
          </Button>
        </div>
      }
    >
      <p className="text-body text-fg-muted">{description}</p>
    </Modal>
  )
}
