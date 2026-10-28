import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

const likeExploreItem = vi.fn()
const unlikeExploreItem = vi.fn()

vi.mock('../../api/exploreApi', () => ({
  likeExploreItem: (itemId: number) => likeExploreItem(itemId),
  unlikeExploreItem: (itemId: number) => unlikeExploreItem(itemId),
}))

const PlaceCard = (await import('../PlaceCard.vue')).default

const place = {
  itemId: 42,
  name: 'Seongsu Onsil',
  brand: null,
  branch: null,
  placeKind: 'RESTAURANT',
  thumbnailUrl: null,
  imageUrls: [],
  region1: 'Seoul',
  region2: 'Seongsu',
  region3: null,
  addressRoad: null,
  addressDetail: null,
  latitude: null,
  longitude: null,
  isActive: true,
  viewCount: 10,
  favoriteCount: 2,
  saved: false,
  hasForeignLang: null,
  hasParking: true,
  reservable: true,
  takeoutAvailable: false,
  cardPaymentAvailable: null,
  smokeFree: null,
  kidFacility: null,
  hasRestroom: false,
}

function mountCard(overrides: Record<string, unknown> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })

  return mount(PlaceCard, {
    global: { plugins: [i18n, [VueQueryPlugin, { queryClient }]] },
    props: { place: { ...place, ...overrides } },
  })
}

describe('PlaceCard', () => {
  beforeEach(() => {
    likeExploreItem.mockReset()
    unlikeExploreItem.mockReset()
    likeExploreItem.mockResolvedValue({ saved: true })
    unlikeExploreItem.mockResolvedValue({ saved: false })
  })

  it('shows press feedback on the clickable card unless reduced motion is requested', () => {
    const wrapper = mountCard()

    expect(wrapper.get('[data-testid="place-card-body"]').classes()).toEqual(
      expect.arrayContaining([
        'transition-transform',
        'motion-reduce:transition-none',
        'motion-safe:[&:active:not(:has([data-card-action]:active))]:scale-[0.98]',
      ]),
    )
  })

  /*
   * 카드 전체가 상세로 가는 자리라 하트와 두 버튼은 클릭이 카드로 올라가지 않게 막고,
   * 누를 때 카드가 줄어들지 않도록 `data-card-action`으로 표시한다(#584).
   */
  it('keeps the heart from opening the detail', async () => {
    const wrapper = mountCard()

    await wrapper.get('button[aria-pressed]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('open')).toBeUndefined()
    expect(wrapper.get('button[aria-pressed]').element.closest('[data-card-action]')).not.toBeNull()
  })

  it('shows how many people saved the place next to the heart', () => {
    const wrapper = mountCard({ favoriteCount: 12500 })
    const heartColumn = wrapper.get('[data-card-action]')

    expect(heartColumn.get('[aria-hidden="true"]:not(svg)').text()).toBe('12.5K')
    expect(heartColumn.get('.sr-only').text()).toBe('12500 people saved this')
  })

  it('puts the heart in the photo column and the buttons in the text column', () => {
    const wrapper = mountCard()
    const [photoColumn, textColumn] = wrapper.get('[data-testid="place-card-body"]').element
      .children

    expect(photoColumn?.querySelector('button[aria-pressed]')).not.toBeNull()
    expect(
      textColumn?.querySelector('button[aria-label="Add Seongsu Onsil to trip"]'),
    ).not.toBeNull()
    expect(
      textColumn?.querySelector('button[aria-label="Find buddy for Seongsu Onsil"]'),
    ).not.toBeNull()
  })

  // 시안에는 기간 자리에 지역만 있다. 부제와 옵션 배지는 상세에서 본다.
  it('renders the place kind and region without the subtitle or option badges', () => {
    const wrapper = mountCard({ brand: 'Onsil', branch: 'Seongsu' })

    expect(wrapper.text()).toContain('Food · Restaurant')
    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).not.toContain('Onsil · Seongsu')
    expect(wrapper.text()).not.toContain('Reservation')
  })

  it('translates operational region values for the list UI', () => {
    const wrapper = mountCard({ region1: '서울', region2: '성수' })

    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).not.toContain('서울')
  })

  it('emits the place id when the card content is activated', async () => {
    const wrapper = mountCard()

    await wrapper.get('[data-testid="place-card-body"]').trigger('click')

    expect(wrapper.emitted('open')).toEqual([[42]])
  })

  it('opens the place once from the title button', async () => {
    const wrapper = mountCard()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Seongsu Onsil')
      ?.trigger('click')

    expect(wrapper.emitted('open')).toEqual([[42]])
  })

  it.each([
    ['Add Seongsu Onsil to trip', 'addToTrip'],
    ['Find buddy for Seongsu Onsil', 'findBuddy'],
  ] as const)('emits %s without opening the detail', async (label, emitted) => {
    const wrapper = mountCard()

    await wrapper.get(`button[aria-label="${label}"]`).trigger('click')

    expect(wrapper.emitted(emitted)).toEqual([[42]])
    expect(wrapper.emitted('open')).toBeUndefined()
  })

  it('renders the heart from the server-provided saved state', () => {
    const unsaved = mountCard()
    const saved = mountCard({ saved: true })

    expect(unsaved.get('button[aria-pressed]').attributes('aria-pressed')).toBe('false')
    expect(saved.get('button[aria-pressed]').attributes('aria-pressed')).toBe('true')
  })

  it('requests a like when the heart is tapped on an unsaved place', async () => {
    const wrapper = mountCard()

    await wrapper.get('button[aria-pressed]').trigger('click')
    await flushPromises()

    expect(likeExploreItem).toHaveBeenCalledWith(42)
    expect(unlikeExploreItem).not.toHaveBeenCalled()
  })

  it('requests an unlike when the heart is tapped on a saved place', async () => {
    const wrapper = mountCard({ saved: true })

    await wrapper.get('button[aria-pressed]').trigger('click')
    await flushPromises()

    expect(unlikeExploreItem).toHaveBeenCalledWith(42)
    expect(likeExploreItem).not.toHaveBeenCalled()
  })
})
