import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import AppBadge from '@/shared/ui/AppBadge.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import AppTicket from '@/shared/ui/AppTicket.vue'

import type { JourneySummary } from '../../api/journeyApi'
import JourneyListCard from '../JourneyListCard.vue'

const journey: JourneySummary = {
  tripId: 42,
  title: 'Seoul Foodie Week',
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  eventCount: 8,
  placeCount: 4,
  coverImageUrl: null,
}

function createRouterStub() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/',
        component: { template: '<div />' },
      },
      {
        path: '/journeys/:tripId',
        name: 'journey-detail',
        component: { template: '<div />' },
      },
      {
        path: '/reports/:reportId',
        name: 'report-detail',
        component: { template: '<div />' },
      },
    ],
  })
}

function mountCard(
  overrides: Partial<JourneySummary> = {},
  props: { status?: 'ongoing' | 'past'; onTrip?: boolean; reportId?: number | null } = {},
) {
  return mount(JourneyListCard, {
    global: { plugins: [i18n, createRouterStub()] },
    props: {
      journey: { ...journey, ...overrides },
      status: 'ongoing' as const,
      ...props,
    },
  })
}

describe('JourneyListCard', () => {
  it('shows press feedback on card links unless reduced motion is requested', () => {
    const wrapper = mountCard({}, { reportId: 7 })

    for (const link of [
      wrapper.get('a[href="/journeys/42"]'),
      wrapper.get('a[href="/reports/7"]'),
    ]) {
      expect(link.classes()).toEqual(
        expect.arrayContaining([
          'transition-transform',
          'motion-reduce:transition-none',
          'active:scale-[0.98]',
          'motion-reduce:active:scale-100',
        ]),
      )
    }
  })

  /*
   * 목록 응답에는 리포트 정보가 없어서 화면이 report feature에서 받아 내려준다(#522).
   * 카드는 값이 있을 때만 링크를 그린다 — 리포트가 없는 여정이 늘 섞여 있다.
   */
  it('links to the report only when the journey has one', () => {
    expect(mountCard({}, { reportId: null }).find('a[href^="/reports/"]').exists()).toBe(false)

    const wrapper = mountCard({}, { reportId: 7 })
    const link = wrapper.get('a[href^="/reports/"]')

    expect(link.attributes('href')).toBe('/reports/7')
    expect(link.text()).toContain('View report')
  })

  /*
   * 🔴 티켓 바깥을 통째로 링크로 감싸면 `View report`가 그 안에 들어가 중첩 인터랙티브가
   * 된다. 스크린 리더에서 링크 안의 링크는 읽히지 않거나 잘못 읽힌다.
   */
  it('does not nest the report link inside the detail link', () => {
    const wrapper = mountCard({}, { reportId: 7 })

    expect(wrapper.findAll('a a')).toHaveLength(0)
    expect(wrapper.findAll('a').length).toBeGreaterThan(1)
  })

  /*
   * 도장은 지금 떠나 있는 여정에만 찍힌다.
   *
   * 대문자는 `TicketStamp`가 CSS로 강제하므로 DOM 글자는 원문 그대로다. 도장 자체는
   * `aria-hidden`이라 상태를 두 번 읽히지 않는다 — 그 몫은 커버 위 `AppBadge`가 진다.
   */
  it('stamps the ticket only while the traveller is actually away', () => {
    const stamp = mountCard({}, { onTrip: true }).get('[aria-hidden="true"].uppercase')

    expect(stamp.text()).toBe('On trip')

    /*
     * 🔴 `status`로 정하지 않는다. 그쪽은 탭 구분이라 `ongoing`에 **예정 여정도 들어간다**
     * (`getJourneyStatus`가 `endDate`만 본다). 시작 전인 여정에 도장이 찍히면 사실이 아닌
     * 상태를 말하게 된다 — #533 리뷰가 잡은 것이다.
     */
    expect(mountCard({}, { status: 'ongoing', onTrip: false }).text()).not.toContain('On trip')
  })

  it('distinguishes scheduled, in-progress, and past journey badges', () => {
    const scheduled = mountCard({}, { status: 'ongoing', onTrip: false })
    const inProgress = mountCard({}, { status: 'ongoing', onTrip: true })
    const past = mountCard({}, { status: 'past', onTrip: false })

    expect(scheduled.getComponent(AppBadge).text()).toBe('Scheduled')
    expect(scheduled.getComponent(AppBadge).classes()).toContain('bg-status-scheduled')
    expect(inProgress.getComponent(AppBadge).text()).toBe('In progress')
    expect(inProgress.getComponent(AppBadge).classes()).toContain('bg-canvas/70')
    expect(past.getComponent(AppBadge).text()).toBe('Past')
    expect(past.getComponent(AppBadge).classes()).toContain('border-hairline')
  })

  /*
   * 커버는 목록 응답의 `coverImageUrl`이다(#424). 값이 오면 사진을, 없으면 자리표시를
   * 그린다. 아직 아무것도 담지 않은 여정이 늘 있어서 두 갈래가 한 화면에 섞인다.
   */
  it('renders the cover photo when the journey has one', () => {
    const wrapper = mountCard({ coverImageUrl: 'https://cdn.test/cover.jpg' })

    const image = wrapper.get('img')

    expect(image.attributes('src')).toBe('https://cdn.test/cover.jpg')
    // 커버는 제목이 이미 말하는 것을 되풀이하는 장식이라 대체 텍스트를 비운다.
    expect(image.attributes('alt')).toBe('')
  })

  /*
   * 빈 값은 `null` 하나가 아니다. 조건을 `!== null`로 두면 빈 문자열이 사진 갈래로 새어
   * `src` 없는 `<img>`가 그려지고 자리표시가 나오지 않는다. 두 경우를 함께 잠근다.
   */
  it.each([
    ['null', null],
    ['빈 문자열', ''],
  ])('falls back to the placeholder when the cover is %s', (_label, coverImageUrl) => {
    const wrapper = mountCard({ coverImageUrl })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.findComponent(ImagePlaceholder).exists()).toBe(true)
  })

  it('links to the journey detail', () => {
    const wrapper = mountCard()

    expect(wrapper.get('a').attributes('href')).toBe('/journeys/42')
  })

  it('shows the full title and stretches the ticket to the tallest carousel card', () => {
    const wrapper = mountCard({ title: 'A very long journey title that needs more than one line' })
    const title = wrapper.get('h3')

    expect(title.classes()).toContain('break-words')
    expect(title.classes()).not.toContain('truncate')
    expect(wrapper.getComponent(AppTicket).classes()).toContain('h-full')
  })
})
