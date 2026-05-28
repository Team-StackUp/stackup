export type ResumeStatus = 'PENDING' | 'ANALYZING' | 'ANALYZED' | 'FAILED'

export type ResumeFileType = 'PDF'

export type Resume = {
  id: number
  originalFilename: string
  filePath: string
  fileType: ResumeFileType
  fileSize: number
  status: ResumeStatus
  createdAt: string
  updatedAt: string
}
