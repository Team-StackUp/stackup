export type ResumeStatus = 'PENDING' | 'ANALYZING' | 'ANALYZED' | 'FAILED'

// WEB = 포트폴리오·블로그·노션 등 URL 자료. S3 파일이 없어 filePath/fileSize 가 비고
// 대신 sourceUrl 이 채워진다.
export type ResumeFileType = 'PDF' | 'WEB'

export type Resume = {
  id: number
  originalFilename: string
  filePath: string | null
  fileType: ResumeFileType
  fileSize: number | null
  sourceUrl: string | null
  status: ResumeStatus
  createdAt: string
  updatedAt: string
}
