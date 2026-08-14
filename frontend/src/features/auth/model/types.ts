export type OAuthProvider = 'GITHUB' | 'GOOGLE'

export type AuthUser = {
  id: number
  provider: OAuthProvider
  /** 화면에 띄우는 이름. provider 와 무관하게 항상 존재한다. */
  displayName: string
  /** GitHub 계정에만 존재. Google 계정은 null — 레포 연동 가능 여부의 근거이기도 하다. */
  githubId: number | null
  githubUsername: string | null
  email: string | null
  avatarUrl: string | null
}

export type OAuthAuthorizeResponse = {
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
