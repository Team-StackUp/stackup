//중복되는 타입일 수 있으나 정의하는 상태가 달라서 일단 분리 X
export type RepositoryStatus =
  | 'PENDING'
  | 'ANALYZING'
  | 'ANALYZED'
  | 'FAILED'

export type CandidateRepositoryResponse = {
  githubRepoId: number
  name: string
  fullName: string
  htmlUrl: string
  defaultBranch: string
  private: boolean
  description: string | null
  alreadyRegistered: boolean
}

export type CandidateRepositoryPageResponse = {
  content: CandidateRepositoryResponse[]
  page: number
  perPage: number
  hasNext: boolean
}

export type RegisteredRepositoryResponse = {
  id: number
  githubRepoId: number
  repoName: string
  repoFullName: string
  repoUrl: string
  defaultBranch: string
  status: RepositoryStatus
  lastSyncedAt: string | null
  createdAt: string
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
