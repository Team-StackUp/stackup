// DOM 요소를 캡처해 A4 PDF 로 저장(클라이언트). 한글은 DOM 렌더를 래스터화하므로 폰트 임베딩 불필요.
// jspdf/html2canvas-pro 는 무거우므로 다운로드 시점에 동적 import (메인 번들 비대화 방지).
// Tailwind v4 의 oklch 색을 지원하려면 html2canvas-pro 사용.
import { applyColorMode, isDarkApplied, readStoredColorMode } from '@/shared/lib/color-mode'

/**
 * 콜백이 도는 동안만 라이트 팔레트를 적용한다. 원래 모드는 무슨 일이 있어도 되돌린다.
 *
 * <p>SEED 는 `<html>` 속성으로 팔레트를 고르므로 속성만 바꾸면 되지만, CSS 변수가 다시
 * 계산되어 화면에 반영될 시간을 한 프레임 준다 — 바로 캡처하면 이전 색이 찍힌다.
 */
async function withLightPalette<T>(run: () => Promise<T>): Promise<T> {
  if (!isDarkApplied()) return run()
  const previous = readStoredColorMode()
  applyColorMode('light-only')
  try {
    await new Promise((resolve) => requestAnimationFrame(() => resolve(null)))
    return await run()
  } finally {
    applyColorMode(previous)
  }
}

export async function downloadElementAsPdf(
  el: HTMLElement,
  filename: string,
): Promise<void> {
  const [{ jsPDF }, { default: html2canvas }] = await Promise.all([
    import('jspdf'),
    import('html2canvas-pro'),
  ])

  // html2canvas 는 '지금 화면에 보이는 대로' 캡처한다. 다크모드 사용자가 그대로 뽑으면
  // 검은 배경에 밝은 글씨인 PDF 가 나온다 — 출력·공유용으로는 쓸 수 없다.
  // 캡처하는 동안만 라이트 팔레트를 강제하고 원래 설정으로 되돌린다.
  const canvas = await withLightPalette(() => html2canvas(el, { scale: 2 }))
  const img = canvas.toDataURL('image/png')

  const pdf = new jsPDF({ unit: 'px', format: 'a4' })
  const pageW = pdf.internal.pageSize.getWidth()
  const pageH = pdf.internal.pageSize.getHeight()
  const imgH = (canvas.height / canvas.width) * pageW

  // 내용이 한 페이지를 넘으면 이미지를 위로 밀며 페이지를 추가.
  let position = 0
  let remaining = imgH
  pdf.addImage(img, 'PNG', 0, position, pageW, imgH)
  remaining -= pageH
  while (remaining > 0) {
    position -= pageH
    pdf.addPage()
    pdf.addImage(img, 'PNG', 0, position, pageW, imgH)
    remaining -= pageH
  }
  pdf.save(filename)
}
