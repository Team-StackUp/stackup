// DOM 요소를 캡처해 A4 PDF 로 저장(클라이언트). 한글은 DOM 렌더를 래스터화하므로 폰트 임베딩 불필요.
// jspdf/html2canvas-pro 는 무거우므로 다운로드 시점에 동적 import (메인 번들 비대화 방지).
// Tailwind v4 의 oklch 색을 지원하려면 html2canvas-pro 사용.
export async function downloadElementAsPdf(
  el: HTMLElement,
  filename: string,
): Promise<void> {
  const [{ jsPDF }, { default: html2canvas }] = await Promise.all([
    import('jspdf'),
    import('html2canvas-pro'),
  ])

  const canvas = await html2canvas(el, { scale: 2 })
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
