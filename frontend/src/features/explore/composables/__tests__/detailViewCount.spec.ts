import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

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
  beforeEach(() => {
    fetchEventDetail.mockReset()
    fetchPlaceDetail.mockReset()
    fetchEventDetail.mockResolvedValue({})
    fetchPlaceDetail.mockResolvedValue({})
  })

  it('상세 화면이 열 때는 조회로 세 달라고 알린다', async () => {
    useEventDetailQuery(42, 'en')
    await (lastOptions().queryFn as () => Promise<unknown>)()

    expect(fetchEventDetail).toHaveBeenCalledWith(42, 'en', { countView: true })
  })

  it('Place 상세도 같다', async () => {
    usePlaceDetailQuery(7, 'en')
    await (lastOptions().queryFn as () => Promise<unknown>)()

    expect(fetchPlaceDetail).toHaveBeenCalledWith(7, 'en', { countView: true })
  })

  it('언어를 바꿔 다시 부를 때는 세지 않는다', async () => {
    /*
     * queryKey에 language가 있어 언어를 바꾸면 새 요청이 나간다. 같은 사람이 같은
     * 화면을 계속 보고 있는 것이라 조회가 아니다.
     */
    const language = ref('en')
    useEventDetailQuery(42, language)
    const queryFn = lastOptions().queryFn as () => Promise<unknown>

    await queryFn()
    language.value = 'ko'
    await queryFn()

    expect(fetchEventDetail).toHaveBeenNthCalledWith(1, 42, 'en', { countView: true })
    expect(fetchEventDetail).toHaveBeenNthCalledWith(2, 42, 'ko', { countView: false })
  })

  it('화면을 떠나지 않고 다른 항목으로 넘어가면 그 항목은 다시 센다', async () => {
    /* 상세는 param 라우트라 항목이 바뀌어도 이 composable이 다시 만들어지지 않는다. */
    const eventId = ref(42)
    useEventDetailQuery(eventId, 'en')
    const queryFn = lastOptions().queryFn as () => Promise<unknown>

    await queryFn()
    eventId.value = 99
    await queryFn()

    expect(fetchEventDetail).toHaveBeenNthCalledWith(2, 99, 'en', { countView: true })
  })

  it('첫 호출이 실패하면 다시 시도할 때 센다', async () => {
    fetchEventDetail.mockRejectedValueOnce(new Error('offline'))
    useEventDetailQuery(42, 'en')
    const queryFn = lastOptions().queryFn as () => Promise<unknown>

    await expect(queryFn()).rejects.toThrow('offline')
    await queryFn()

    expect(fetchEventDetail).toHaveBeenNthCalledWith(2, 42, 'en', { countView: true })
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
