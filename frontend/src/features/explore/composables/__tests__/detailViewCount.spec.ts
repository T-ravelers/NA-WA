import { describe, expect, it, vi } from 'vitest'

const useQuery = vi.fn()
const fetchEventDetail = vi.fn()
const fetchPlaceDetail = vi.fn()

vi.mock('@tanstack/vue-query', () => ({
  useQuery: (options: Record<string, unknown>) => useQuery(options),
}))

vi.mock('../../api/exploreApi', () => ({
  fetchEventDetail: (...args: unknown[]) => fetchEventDetail(...args),
  fetchPlaceDetail: (...args: unknown[]) => fetchPlaceDetail(...args),
}))

const { useEventDetailQuery } = await import('../useEventDetailQuery')
const { usePlaceDetailQuery } = await import('../usePlaceDetailQuery')

/** 마지막으로 `useQuery`에 넘어간 설정. */
function lastOptions(): Record<string, unknown> {
  const calls = useQuery.mock.calls
  const call: unknown[] | undefined = calls[calls.length - 1]
  if (call === undefined) throw new Error('useQuery was never called.')

  return call[0] as Record<string, unknown>
}

describe('상세 조회수', () => {
  it('상세 화면이 열 때는 조회로 세 달라고 알린다', () => {
    useEventDetailQuery(42, 'en')
    ;(lastOptions().queryFn as () => unknown)()

    expect(fetchEventDetail).toHaveBeenCalledWith(42, 'en', { countView: true })
  })

  it('Place 상세도 같다', () => {
    usePlaceDetailQuery(7, 'en')
    ;(lastOptions().queryFn as () => unknown)()

    expect(fetchPlaceDetail).toHaveBeenCalledWith(7, 'en', { countView: true })
  })

  it('창으로 돌아왔다고 다시 부르지 않는다', () => {
    /*
     * 이 호출은 조회수를 1 올린다. 자동 재요청이 켜져 있으면 화면을 켜 둔 채 다른 앱에
     * 다녀오기만 해도 조회수가 오른다 — 사람이 상세를 다시 연 것이 아니다.
     */
    useEventDetailQuery(42, 'en')
    expect(lastOptions().refetchOnWindowFocus).toBe(false)

    usePlaceDetailQuery(7, 'en')
    expect(lastOptions().refetchOnWindowFocus).toBe(false)
  })
})
