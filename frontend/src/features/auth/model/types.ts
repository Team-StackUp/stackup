export type AuthUser = {
  id: number
  githubId: number
  githubUsername: string
  email: string | null
  avatarUrl: string | null
}

export type GithubAuthorizeResponse = {
  authorizationUrl: string
  state: string
}

export type LoginResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: AuthUser
  isNewUser: boolean
}

export type RefreshResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}
