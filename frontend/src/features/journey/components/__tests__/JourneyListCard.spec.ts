import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'

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
})
