import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 탐색 목록과 상세 화면이 함께 사용하는 Event 찜 상태.
 *
 * 찜 등록·취소 백엔드 API가 연결되기 전까지는 현재 SPA 세션 안에서 상태를 공유한다.
 */
export const useSavedEventsStore = defineStore('explore-saved-events', () => {
  const savedEventIds = ref<Set<number>>(new Set())

  function isSaved(eventId: number): boolean {
    return savedEventIds.value.has(eventId)
  }

  function toggle(eventId: number): boolean {
    const next = new Set(savedEventIds.value)

    if (next.has(eventId)) next.delete(eventId)
    else next.add(eventId)

    savedEventIds.value = next
    return next.has(eventId)
  }

  return { isSaved, toggle }
})
