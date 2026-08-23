import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

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
        path: '/journeys/:tripId',
        name: 'journey-detail',
        component: { template: '<div />' },
      },
    ],
  })
}

function mountCard(overrides: Partial<JourneySummary> = {}) {
  return mount(JourneyListCard, {
    global: { plugins: [i18n, createRouterStub()] },
    props: {
      journey: { ...journey, ...overrides },
      status: 'ongoing' as const,
      statusLabel: 'Ongoing',
    },
  })
}

describe('JourneyListCard', () => {
  /*
   * 커버는 목록 응답의 `coverImageUrl`이다(#424). 값이 오면 사진을, 없으면 자리표시를
   * 그린다. 수집 데이터의 썸네일 결측이 이벤트 약 33%·장소 약 44%라 두 갈래가 한
   * 화면에 섞이는 것이 기본 상태다.
   */
  it('renders the cover photo when the journey has one', () => {
    const wrapper = mountCard({ coverImageUrl: 'https://cdn.test/cover.jpg' })

    const image = wrapper.get('img')

    expect(image.attributes('src')).toBe('https://cdn.test/cover.jpg')
    // 커버는 제목이 이미 말하는 것을 되풀이하는 장식이라 대체 텍스트를 비운다.
    expect(image.attributes('alt')).toBe('')
  })

  it('falls back to the placeholder when the journey has no cover', () => {
    const wrapper = mountCard({ coverImageUrl: null })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('[role="presentation"], [role="img"]').exists()).toBe(true)
  })

  it('links to the journey detail', () => {
    const wrapper = mountCard()

    expect(wrapper.get('a').attributes('href')).toBe('/journeys/42')
  })
})
