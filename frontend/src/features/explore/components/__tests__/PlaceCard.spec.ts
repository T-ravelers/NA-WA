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

  it('renders the place kind, region and available options', () => {
    const wrapper = mountCard()

    expect(wrapper.text()).toContain('Food · Restaurant')
    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).toContain('Reservation')
    expect(wrapper.text()).not.toContain('Takeout')
  })

  it('translates operational region values for the list UI', () => {
    const wrapper = mountCard({ region1: '서울', region2: '성수' })

    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).not.toContain('서울')
  })

  it('emits the place id when the card content is activated', async () => {
    const wrapper = mountCard()

    await wrapper.get('button[aria-label="Open Seongsu Onsil"]').trigger('click')

    expect(wrapper.emitted('open')).toEqual([[42]])
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
