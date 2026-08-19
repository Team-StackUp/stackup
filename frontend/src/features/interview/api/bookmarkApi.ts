import { apiClient } from '@/shared/api'
import type { components } from '@/shared/api/generated'

type S = components['schemas']
export type BookmarkedQuestion = S['BookmarkedQuestionResponse']

export async function listBookmarks(): Promise<BookmarkedQuestion[]> {
  return (await apiClient.get<BookmarkedQuestion[]>('/api/users/me/bookmarks')).data
}

// 토글이 아니라 명시적 상태를 보낸다 — 토글이면 재전송·더블클릭이 상태를 뒤집는다.
export async function setQuestionBookmark(
  sessionId: number,
  messageId: number,
  bookmarked: boolean,
): Promise<S['QuestionBookmarkResponse']> {
  return (
    await apiClient.put<S['QuestionBookmarkResponse']>(
      `/api/sessions/${sessionId}/messages/${messageId}/bookmark`,
      { bookmarked },
    )
  ).data
}
