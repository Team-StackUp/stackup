import type { components } from '@/shared/api/generated'

type Schemas = components['schemas']

export type Session = Schemas['SessionResponse']
export type Message = Schemas['MessageResponse']
export type SessionCreateRequest = Schemas['SessionCreateRequest']

export type SessionStatus = NonNullable<Session['status']>
export type SessionMode = NonNullable<SessionCreateRequest['mode']>
export type JobCategory = NonNullable<SessionCreateRequest['jobCategories']>[number]
export type MessageRole = NonNullable<Message['role']>
