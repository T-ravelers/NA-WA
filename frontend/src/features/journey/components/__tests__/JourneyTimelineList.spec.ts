import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import type { JourneyTimelineDay } from '../../api/journeyApi'
import { journeyAppointmentIntegrationKey } from '../../model/appointmentIntegration'
import JourneyTimelineList from '../JourneyTimelineList.vue'

function createRouterStub() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/explore', name: 'explore', component: { template: '<div>Explore</div>' } },
      {
        path: '/explore/events/:eventId',
        name: 'explore-event-detail',
        component: { template: '<div />' },
      },
      {
        path: '/explore/places/:placeId',
        name: 'explore-place-detail',
        component: { template: '<div />' },
      },
      { path: '/appointments', name: 'appointment-list', component: { template: '<div />' } },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div />' },
      },
      { path: '/journeys/:tripId', name: 'journey-detail', component: { template: '<div />' } },
    ],
  })
}

type TimelineItem = JourneyTimelineDay['items'][number]

interface ItemOverrides extends Partial<Omit<TimelineItem, 'exploreItem'>> {
  exploreItem?: Partial<TimelineItem['exploreItem']>
}

function makeItem(title: string, overrides: ItemOverrides = {}): TimelineItem {
  const { exploreItem, ...rest } = overrides

  return {
    tripItemId: 1,
    itemId: 11,
    status: 'ADDED',
    displayOrder: 0,
    note: null,
    ...rest,
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
      ...exploreItem,
    },
  }
}

/** 확정 항목은 약속을 갖는다. `appointmentId`는 #189가 응답에 추가한 필드다. */
function confirmedItem(title: string, appointmentId: number | undefined): TimelineItem {
  return makeItem(title, {
    status: 'CONFIRMED',
    appointment: {
      ...(appointmentId === undefined ? {} : { appointmentId }),
      activityStartAt: '2026-08-11T10:20:00',
      activityEndAt: '2026-08-11T12:00:00',
      appointmentStatus: 'RECRUITING',
    } as TimelineItem['appointment'],
  })
}

function dayWith(visitDate: string, ...items: TimelineItem[]): JourneyTimelineDay {
  return { visitDate, items }
}

function dayWithItem(visitDate: string, title: string): JourneyTimelineDay {
  return dayWith(visitDate, makeItem(title))
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
    global: {
      plugins: [i18n, router],
      provide: {
        [journeyAppointmentIntegrationKey as symbol]: {
          useAppointmentMembersQuery: () => ({
            data: ref([]),
            isPending: ref(false),
            isError: ref(false),
          }),
        },
      },
    },
  })
}

function addLinks(wrapper: Awaited<ReturnType<typeof mountList>>, prefix: string) {
  return wrapper.findAll(`a[aria-label^="${prefix}"]`)
}

function ctaFor(wrapper: Awaited<ReturnType<typeof mountList>>, name: string) {
  return wrapper.findAll(`a[aria-label="${name}"]`)[0]
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

  it('offers add buttons only for dates inside the journey range', async () => {
    const wrapper = await mountList({
      days: [dayWithItem('2026-08-20', 'Late addition')],
      startDate: '2026-08-10',
      endDate: '2026-08-11',
    })

    // 기간 밖 날짜로 담으면 서버가 `JOURNEY-007`로 거절하므로 진입로를 두지 않는다.
    expect(addLinks(wrapper, 'Add event on')).toHaveLength(2)
    expect(addLinks(wrapper, 'Add place on')).toHaveLength(2)
    expect(wrapper.findAll('a[href*="2026-08-20"]')).toHaveLength(0)
  })

  it('links Event detail to the event page and Place detail to the place page', async () => {
    const wrapper = await mountList({
      days: [
        dayWith(
          '2026-08-10',
          makeItem('Night market'),
          makeItem('Roastery', {
            tripItemId: 2,
            itemId: 22,
            exploreItem: { itemType: 'PLACE' },
          }),
        ),
      ],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    expect(ctaFor(wrapper, 'Event detail for Night market')?.attributes('href')).toBe(
      '/explore/events/11',
    )
    expect(ctaFor(wrapper, 'Place detail for Roastery')?.attributes('href')).toBe(
      '/explore/places/22',
    )
  })

  it('colours every card by the shared consumption area of its real kind value', async () => {
    const wrapper = await mountList({
      days: [
        dayWith(
          '2026-08-10',
          makeItem('Concert', {
            eventDetail: {
              eventKind: 'CONCERT',
              startDate: null,
              endDate: null,
              organizer: null,
              reservationUrl: null,
              venueName: null,
            },
          }),
          makeItem('Popup', {
            tripItemId: 2,
            eventDetail: {
              eventKind: 'POPUP',
              startDate: null,
              endDate: null,
              organizer: null,
              reservationUrl: null,
              venueName: null,
            },
          }),
          makeItem('Beauty shop', {
            tripItemId: 3,
            exploreItem: { itemType: 'PLACE' },
            placeDetail: {
              placeKind: '뷰티매장',
              addressDetail: null,
              menuSummary: null,
              isActive: true,
            },
          }),
          makeItem('Cafe', {
            tripItemId: 4,
            exploreItem: { itemType: 'PLACE' },
            placeDetail: {
              placeKind: '카페',
              addressDetail: null,
              menuSummary: null,
              isActive: true,
            },
          }),
        ),
      ],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    expect(wrapper.text()).toContain('Shows')
    expect(wrapper.text()).toContain('Shopping')
    expect(wrapper.text()).toContain('Beauty')
    expect(wrapper.text()).toContain('Food')
  })

  it('prefers the translated address over untranslated region fields', async () => {
    const wrapper = await mountList({
      days: [
        dayWith(
          '2026-08-10',
          makeItem('Seongsu Onsil', {
            exploreItem: {
              itemType: 'PLACE',
              location: {
                region1: '서울',
                region2: '성수',
                region3: null,
                addressRoad: 'Seongsu-dong, Seongdong-gu, Seoul',
                addressDetail: null,
                latitude: null,
                longitude: null,
              },
            },
          }),
        ),
      ],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    expect(wrapper.text()).toContain('Seongsu-dong, Seongdong-gu, Seoul')
    expect(wrapper.text()).not.toContain('성수')
  })

  it('shows the coordinate-derived distance to the next stop', async () => {
    const first = makeItem('Gwangjang Market')
    first.exploreItem.location.latitude = 37.5701
    first.exploreItem.location.longitude = 126.9997
    const second = makeItem('Olive Young Myeongdong', { tripItemId: 2, itemId: 22 })
    second.exploreItem.location.latitude = 37.5604
    second.exploreItem.location.longitude = 126.9896

    const wrapper = await mountList({
      days: [dayWith('2026-08-10', first, second)],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    expect(wrapper.text()).toContain('1.4 km')
    expect(wrapper.text().match(/km/g)).toHaveLength(1)
  })

  it('does not connect the last stop of one day to the first stop of the next day', async () => {
    const firstDayStop = makeItem('Gwangjang Market')
    firstDayStop.exploreItem.location.latitude = 37.5701
    firstDayStop.exploreItem.location.longitude = 126.9997
    const nextDayStop = makeItem('Olive Young Myeongdong', { tripItemId: 2, itemId: 22 })
    nextDayStop.exploreItem.location.latitude = 37.5604
    nextDayStop.exploreItem.location.longitude = 126.9896

    const wrapper = await mountList({
      days: [dayWith('2026-08-10', firstDayStop), dayWith('2026-08-11', nextDayStop)],
      startDate: '2026-08-10',
      endDate: '2026-08-11',
    })

    expect(wrapper.text()).not.toContain('km')
  })

  it('keeps companion actions out of the compact Figma timeline card', async () => {
    const wrapper = await mountList({
      days: [dayWithItem('2026-08-10', 'Night market')],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    expect(wrapper.text()).not.toContain('Find companions')
    expect(wrapper.findAll('a[href^="/appointments"]')).toHaveLength(0)
  })

  it('shows participant data without adding a second appointment CTA', async () => {
    const wrapper = await mountList({
      days: [dayWith('2026-08-10', confirmedItem('Night market', 501))],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    expect(wrapper.text()).not.toContain('View companions')
    expect(wrapper.findAll('a[href^="/appointments"]')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('Find companions')
  })

  it('drops the companions CTA when a confirmed item has no appointment id', async () => {
    const wrapper = await mountList({
      days: [dayWith('2026-08-10', confirmedItem('Night market', undefined))],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    // 갈 곳이 없는 링크를 만들지 않는다. 상세 진입은 그대로 남는다.
    expect(wrapper.text()).not.toContain('View companions')
    expect(wrapper.findAll('a[href^="/appointments"]')).toHaveLength(0)
    expect(ctaFor(wrapper, 'Event detail for Night market')?.exists()).toBe(true)
  })

  it('gives every repeated detail card an item-specific accessible name', async () => {
    const wrapper = await mountList({
      days: [
        dayWith(
          '2026-08-10',
          makeItem('Night market'),
          makeItem('Roastery', { tripItemId: 2, itemId: 22 }),
        ),
      ],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    const names = wrapper
      .findAll('a[aria-label^="Event detail for"]')
      .map((link) => link.attributes('aria-label'))

    expect(new Set(names).size).toBe(2)
    // 보이는 라벨이 접근 가능한 이름 앞부분에 그대로 들어간다 (WCAG 2.5.3).
    names.forEach((name) => expect(name).toMatch(/^Event detail for .+/))
  })

  it('keeps the remove control visible on the card instead of hiding it from sight', async () => {
    const wrapper = await mountList({
      days: [dayWithItem('2026-08-10', 'Night market')],
      startDate: '2026-08-10',
      endDate: '2026-08-10',
    })

    const remove = wrapper.find('[data-testid="itinerary-remove-1"]')

    expect(remove.exists()).toBe(true)
    expect(remove.classes()).not.toContain('sr-only')
    expect(remove.attributes('aria-label')).toBe('Remove Night market from itinerary')
    // 카드 전체가 상세 링크라 삭제 버튼이 그 안에 들어가면 링크가 중첩된다.
    expect(remove.element.closest('a')).toBeNull()
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
