export type RepositoryStatus = 'PENDING' | 'ANALYZING' | 'ANALYZED' | 'FAILED'

export type RegisteredRepository = {
  id: number
  githubRepoId: number
  repoName: string
  repoFullName: string
  repoUrl: string
  defaultBranch: string | null
  status: RepositoryStatus
  lastSyncedAt: string | null
  createdAt: string
  updatedAt: string
}

export type CandidateRepository = {
  githubRepoId: number
  name: string
  fullName: string
  htmlUrl: string
  defaultBranch: string | null
  isPrivate: boolean
  alreadyRegistered: boolean
}

export type RegisterRepositoryInput = {
  githubRepoId: number
  fullName: string
}
