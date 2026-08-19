import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from '@/shared/ui'
import { listBookmarks, setQuestionBookmark } from '../api/bookmarkApi'
import type { BookmarkedQuestion } from '../api/bookmarkApi'
import { messageKeys } from './useSessionMessages'

export const bookmarkKeys = {
  all: ['bookmarks'] as const,
}

export function useBookmarks() {
  return useQuery<BookmarkedQuestion[]>({
    queryKey: bookmarkKeys.all,
    queryFn: listBookmarks,
  })
}

export function useSetQuestionBookmark(sessionId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ messageId, bookmarked }: { messageId: number; bookmarked: boolean }) =>
      setQuestionBookmark(sessionId, messageId, bookmarked),
    onSuccess: (_data, variables) => {
      // 전사(별 표시 상태)와 오답노트 목록 양쪽이 바뀐다.
      void queryClient.invalidateQueries({ queryKey: messageKeys.list(sessionId) })
      void queryClient.invalidateQueries({ queryKey: bookmarkKeys.all })
      toast.success(
        variables.bookmarked ? '오답노트에 담았어요' : '오답노트에서 뺐어요',
      )
    },
    onError: () => toast.error('오답노트를 바꾸지 못했어요. 다시 시도해 주세요.'),
  })
}
