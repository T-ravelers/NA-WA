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

  it('emits the event id when the card is activated', async () => {
    const wrapper = mountCard()

    await wrapper.get('article').trigger('click')

    expect(wrapper.emitted('open')).toEqual([[42]])
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
