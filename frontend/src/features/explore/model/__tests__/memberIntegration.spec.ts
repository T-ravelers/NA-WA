import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'

import { i18n } from '@/app/i18n'
import { applyLocale } from '@/app/i18n/applyLocale'
import { DEFAULT_LOCALE } from '@/shared/i18n/locales'

import type { EventSummary } from '../eventExplore'
import type { PlaceSummary } from '../placeExplore'

const fetchEventList = vi.fn()
const fetchPlaceList = vi.fn()

vi.mock('../../api/exploreApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/exploreApi')>()),
  fetchEventList: (filters: unknown) => fetchEventList(filters),
  fetchPlaceList: (filters: unknown) => fetchPlaceList(filters),
}))

const { useSavedExploreItemsQuery } = await import('../memberIntegration')

const savedEvent: EventSummary = {
  itemId: 11,
  eventKind: 'POPUP',
  status: 'ONGOING',
  title: 'Seongsu Beauty Pop-up',
  subtitle: null,
  thumbnailUrl: null,
  region1: '서울',
  region2: '성수',
  region3: null,
  latitude: null,
  longitude: null,
  startDate: '2026-08-01',
  endDate: '2026-08-31',
  saved: true,
}

const savedPlace: PlaceSummary = {
  itemId: 22,
  name: 'Seongsu Coffee Lab',
  brand: null,
  branch: null,
  placeKind: 'CAFE',
  thumbnailUrl: null,
  imageUrls: [],
  region1: '서울',
  region2: '성수',
  region3: null,
  addressRoad: null,
  addressDetail: null,
  latitude: null,
  longitude: null,
  isActive: true,
  viewCount: 0,
  favoriteCount: 0,
  saved: true,
}

function pageOf<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 30,
    totalElements: content.length,
    totalPages: 1,
    hasNext: false,
  }
}

/** 찜 항목의 `제목|지역명`을 한 줄씩 그린다. 화면이 실제로 읽는 두 값만 본다. */
const Harness = defineComponent({
  props: { kind: { type: String as () => 'EVENT' | 'PLACE', required: true } },
  setup(props) {
    const kind = ref(props.kind)
    const query = useSavedExploreItemsQuery(kind, ref(true))
    return { items: query.data }
  },
  template: `<ul><li v-for="item in items" :key="item.itemId">{{ item.title }}|{{ item.subtitle }}</li></ul>`,
})

async function mountHarness(kind: 'EVENT' | 'PLACE') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(Harness, {
    props: { kind },
    global: { plugins: [i18n, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return wrapper
}

describe('useSavedExploreItemsQuery', () => {
  beforeEach(() => {
    fetchEventList.mockReset()
    fetchPlaceList.mockReset()
    fetchEventList.mockResolvedValue(pageOf([savedEvent]))
    fetchPlaceList.mockResolvedValue(pageOf([savedPlace]))
    applyLocale(DEFAULT_LOCALE)
  })

  afterEach(() => {
    applyLocale(DEFAULT_LOCALE)
  })

  it('언어를 바꾸면 이미 받아 둔 찜 Event의 지역명도 새 언어로 바뀐다', async () => {
    const wrapper = await mountHarness('EVENT')
    expect(wrapper.text()).toContain('Seongsu Beauty Pop-up|Seoul · Seongsu')

    applyLocale('ja')
    await flushPromises()

    expect(wrapper.text()).toContain('Seongsu Beauty Pop-up|ソウル · 聖水')
    // 지역명은 표시할 때 번역한다. 언어를 바꿔도 같은 목록을 다시 받아 오지 않는다.
    expect(fetchEventList).toHaveBeenCalledTimes(1)
  })

  it('언어를 바꾸면 이미 받아 둔 찜 Place의 지역명도 새 언어로 바뀐다', async () => {
    const wrapper = await mountHarness('PLACE')
    expect(wrapper.text()).toContain('Seongsu Coffee Lab|Seoul · Seongsu')

    applyLocale('ja')
    await flushPromises()

    expect(wrapper.text()).toContain('Seongsu Coffee Lab|ソウル · 聖水')
    expect(fetchPlaceList).toHaveBeenCalledTimes(1)
  })

  it('로케일 문구가 없는 지역은 서버가 준 값을 그대로 쓴다', async () => {
    fetchEventList.mockResolvedValue(pageOf([{ ...savedEvent, region1: '제주도', region2: null }]))

    const wrapper = await mountHarness('EVENT')

    expect(wrapper.text()).toContain('Seongsu Beauty Pop-up|제주도')
  })
})
