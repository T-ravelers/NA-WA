import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'

import { i18n } from '@/app/i18n'

const likeExploreItem = vi.fn()
const unlikeExploreItem = vi.fn()

vi.mock('../../api/exploreApi', () => ({
  likeExploreItem: (itemId: number) => likeExploreItem(itemId),
  unlikeExploreItem: (itemId: number) => unlikeExploreItem(itemId),
}))

const EventCard = (await import('../EventCard.vue')).default

const event = {
  itemId: 42,
  eventKind: 'POPUP' as const,
  status: 'ONGOING' as const,
  title: 'Sample event',
  subtitle: null,
  thumbnailUrl: null,
  region1: 'Seoul',
  region2: null,
  region3: null,
  latitude: null,
  longitude: null,
  startDate: '2026-08-01',
  endDate: '2026-08-31',
  isPermanent: false,
  saved: false,
}

function mountCard(overrides: Record<string, unknown> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return mount(EventCard, {
    global: { plugins: [i18n, [VueQueryPlugin, { queryClient }]] },
    props: { event: { ...event, ...overrides } },
  })
}

describe('EventCard', () => {
  beforeEach(() => {
    likeExploreItem.mockReset()
    unlikeExploreItem.mockReset()
    likeExploreItem.mockResolvedValue({ saved: true })
    unlikeExploreItem.mockResolvedValue({ saved: false })
  })

  it('shows press feedback on the clickable card unless reduced motion is requested', () => {
    const wrapper = mountCard()

    expect(wrapper.get('article').classes()).toEqual(
      expect.arrayContaining([
        'transition-transform',
        'motion-reduce:transition-none',
        'active:scale-[0.98]',
        'motion-reduce:active:scale-100',
      ]),
    )
  })

  it('emits the event id when the card is activated', async () => {
    const wrapper = mountCard()

    await wrapper.get('article').trigger('click')

    expect(wrapper.emitted('open')).toEqual([[42]])
  })

  /*
   * 상태를 칩에서 점 + 색 글자로 바꿨는데 이 자리를 보는 테스트가 없었다. 색은 상태마다
   * 다르고 종료는 캡션과 섞이지 않도록 한 단계 밝게 뒀다 — 다음 사람이 이 표를 건드리면
   * 여기서 걸린다.
   */
  it.each([
    ['ONGOING', 'Ongoing', 'text-status-ongoing', 'bg-status-ongoing'],
    ['SCHEDULED', 'Opening soon', 'text-status-scheduled', 'bg-status-scheduled'],
    ['ENDED', 'Ended', 'text-ink-2', 'bg-ink-2'],
  ])('marks a %s event with its own label and colour', (status, label, textClass, dotClass) => {
    const wrapper = mountCard({ status })
    const line = wrapper.get(`[data-testid="event-status"]`)

    expect(line.text()).toBe(label)
    expect(line.classes()).toContain(textClass)
    expect(line.get('span').classes()).toContain(dotClass)
  })

  it('renders the period when both dates are present', () => {
    const wrapper = mountCard()

    expect(wrapper.text()).toContain('2026.08.01 ~ 2026.08.31')
  })

  // `end_date`는 널을 허용하며 로컬 시드 854건 중 74건이 널이다. 가드가 없으면 렌더
  // 예외가 형제 vnode까지 무너뜨려 카드 한 장이 아니라 목록 전체가 사라진다.
  it('renders a card whose end date is missing', () => {
    const wrapper = mountCard({ endDate: null })

    expect(wrapper.text()).toContain('Sample event')
    expect(wrapper.text()).toContain('2026.08.01')
    expect(wrapper.text()).not.toContain('~')
  })

  /*
   * `isPermanent`는 "상시 운영"이 아니라 종료일을 받지 못했다는 뜻이다(로컬 시드 74건은
   * 전부 축제·팝업·콘서트다). 시작일만 찍으면 "그 하루짜리 지난 행사"로 읽히고, 상시라고
   * 적으면 끝난 콘서트가 영원히 열려 있다고 단언하게 된다. 끝을 모른다는 것만 적는다.
   */
  it('renders an open-ended period when the end date is unknown', () => {
    const wrapper = mountCard({ isPermanent: true, endDate: null })

    expect(wrapper.text()).toContain('From 2026.08.01')
    expect(wrapper.text()).not.toContain('~')
  })

  it('renders no period when an open-ended event has no start date either', () => {
    const wrapper = mountCard({ isPermanent: true, startDate: null, endDate: null })

    expect(wrapper.text()).toContain('Sample event')
    expect(wrapper.text()).not.toContain('From')
  })

  it('renders a card whose dates are both missing', () => {
    const wrapper = mountCard({ startDate: null, endDate: null })

    expect(wrapper.text()).toContain('Sample event')
    expect(wrapper.text()).not.toContain('~')
  })

  it('renders the heart from the server-provided saved state', () => {
    const unsaved = mountCard()
    const saved = mountCard({ saved: true })

    expect(unsaved.get('button[aria-pressed]').attributes('aria-pressed')).toBe('false')
    expect(saved.get('button[aria-pressed]').attributes('aria-pressed')).toBe('true')
  })

  it('requests a like when the heart is tapped on an unsaved event', async () => {
    const wrapper = mountCard()

    await wrapper.get('button[aria-pressed]').trigger('click')
    await flushPromises()

    expect(likeExploreItem).toHaveBeenCalledWith(42)
    expect(unlikeExploreItem).not.toHaveBeenCalled()
  })

  it('requests an unlike when the heart is tapped on a saved event', async () => {
    const wrapper = mountCard({ saved: true })

    await wrapper.get('button[aria-pressed]').trigger('click')
    await flushPromises()

    expect(unlikeExploreItem).toHaveBeenCalledWith(42)
    expect(likeExploreItem).not.toHaveBeenCalled()
  })
})
