export type ResumeStatus = 'PENDING' | 'ANALYZING' | 'ANALYZED' | 'FAILED'

export type ResumeResponse = {
  id: number
  originalFilename: string
  fileSize: number
  status: ResumeStatus
  createdAt: string
  updatedAt: string
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
