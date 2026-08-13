import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import type { JourneyTimelineDay } from '../../api/journeyApi'
import JourneyTimelineList from '../JourneyTimelineList.vue'

function createRouterStub() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/explore', name: 'explore', component: { template: '<div>Explore</div>' } },
      { path: '/journeys/:tripId', name: 'journey-detail', component: { template: '<div />' } },
    ],
  })
}

function dayWithItem(visitDate: string, title: string): JourneyTimelineDay {
  return {
    visitDate,
    items: [
      {
        tripItemId: 1,
        itemId: 11,
        status: 'ADDED',
        displayOrder: 0,
        note: null,
        exploreItem: {
          itemType: 'EVENT',
          title,
          thumbnailUrl: null,
          imageUrls: [],
          location: {
            region1: 'Seoul',
            region2: null,
            region3: null,
            addressRoad: null,
            addressDetail: null,
            latitude: null,
            longitude: null,
          },
        },
      },
    ],
  }
}

async function mountList(props: {
  days: JourneyTimelineDay[]
  startDate: string
  endDate: string
  tripId?: number
}) {
  const router = createRouterStub()
  await router.push('/journeys/7')
  await router.isReady()

  return mount(JourneyTimelineList, {
    props: { tripId: 42, ...props },
    global: { plugins: [i18n, router] },
  })
}

function addLinks(wrapper: Awaited<ReturnType<typeof mountList>>, prefix: string) {
  return wrapper.findAll(`a[aria-label^="${prefix}"]`)
}

describe('JourneyTimelineList', () => {
  it('renders every journey date even when only one has items', async () => {
    const wrapper = await mountList({
      days: [dayWithItem('2026-08-11', 'Night market')],
      startDate: '2026-08-10',
      endDate: '2026-08-12',
    })

    expect(wrapper.findAll('time[datetime^="2026-08-1"]')).toHaveLength(3)
    expect(wrapper.text()).toContain('Day 1')
    expect(wrapper.text()).toContain('Day 2')
    expect(wrapper.text()).toContain('Day 3')
    expect(wrapper.text()).toContain('Night market')
  })

  it('keeps both add buttons on days that already have items', async () => {
    const wrapper = await mountList({
      days: [dayWithItem('2026-08-11', 'Night market')],
      startDate: '2026-08-11',
      endDate: '2026-08-11',
    })

    expect(addLinks(wrapper, 'Add event on')).toHaveLength(1)
    expect(addLinks(wrapper, 'Add place on')).toHaveLength(1)
  })

  it('sends Add event to the events tab filtered to that date', async () => {
    const wrapper = await mountList({
      days: [],
      startDate: '2026-08-10',
      endDate: '2026-08-11',
    })

    const hrefs = addLinks(wrapper, 'Add event on').map((link) => link.attributes('href'))

    expect(hrefs).toEqual([
      '/explore?journeyId=42&startDate=2026-08-10&endDate=2026-08-10',
      '/explore?journeyId=42&startDate=2026-08-11&endDate=2026-08-11',
    ])
  })

  it('sends Add place to the places tab with the same date', async () => {
    const wrapper = await mountList({
      days: [],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    expect(addLinks(wrapper, 'Add place on')[0]?.attributes('href')).toBe(
      '/explore?tab=places&journeyId=42&startDate=2026-08-10&endDate=2026-08-10',
    )
  })

  it('gives each repeated button a date-specific accessible name', async () => {
    const wrapper = await mountList({
      days: [],
      startDate: '2026-08-10',
      endDate: '2026-08-12',
    })

    const names = addLinks(wrapper, 'Add event on').map((link) => link.attributes('aria-label'))

    expect(new Set(names).size).toBe(3)
    // 보이는 라벨이 접근 가능한 이름 앞부분에 그대로 들어간다 (WCAG 2.5.3).
    names.forEach((name) => expect(name).toMatch(/^Add event on .+/))
  })

  it('still shows items whose visit date falls outside the journey range', async () => {
    const wrapper = await mountList({
      days: [dayWithItem('2026-08-20', 'Late addition')],
      startDate: '2026-08-10',
      endDate: '2026-08-11',
    })

    const dates = wrapper.findAll('time').map((node) => node.attributes('datetime'))

    expect(dates).toEqual(['2026-08-10', '2026-08-11', '2026-08-20'])
    expect(wrapper.text()).toContain('Late addition')
    // 기간 밖 날짜에는 붙일 순번이 없다.
    expect(wrapper.text()).not.toContain('Day 3')
  })

  it('handles a journey longer than a month', async () => {
    const wrapper = await mountList({
      days: [],
      startDate: '2026-08-10',
      endDate: '2026-09-09',
    })

    expect(wrapper.findAll('time')).toHaveLength(31)
    expect(wrapper.text()).toContain('Day 31')
  })
})
