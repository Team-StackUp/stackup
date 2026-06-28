import type { components } from '@/shared/api/generated'

type Schemas = components['schemas']

export type CoverLetter = Schemas['CoverLetterResponse']
export type CoverLetterCreateRequest = Schemas['CoverLetterCreateRequest']
export type CoverLetterItem = Schemas['Item']
export type CoverLetterStatus = NonNullable<CoverLetter['status']>
