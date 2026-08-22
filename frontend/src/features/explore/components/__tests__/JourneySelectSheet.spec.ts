import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import JourneySelectSheet from '../JourneySelectSheet.vue'

const journeys = [
  {
    tripId: 12,
    title: 'Seoul Foodie Week',
    startDate: '2026-03-28',
    endDate: '2026-04-01',
  },
]

/** 3/28~4/1 여정과 겹치는 이벤트 기간. */
const overlappingItem = { itemStartDate: '2026-03-30', itemEndDate: '2026-04-05' }

function mountSheet(props: Record<string, unknown>) {
  return mount(JourneySelectSheet, {
    global: { plugins: [i18n] },
    props: { journeys, ...overlappingItem, ...props },
  })
}

function journeyButton(wrapper: ReturnType<typeof mountSheet>) {
  return wrapper.findAll('button').find((button) => button.text().includes('Seoul Foodie'))
}

describe('JourneySelectSheet', () => {
  it('emits the selected journey', async () => {
    const wrapper = mountSheet({})

    await journeyButton(wrapper)?.trigger('click')

    expect(wrapper.emitted('select')).toEqual([[12]])
  })

  it('closes when the scrim is pressed', async () => {
    const wrapper = mountSheet({})

    await wrapper.get('button[aria-label="Close journey selector"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  /*
   * 목록에서 감추면 사용자는 자기 여정이 없어진 줄 안다. 보이되 고를 수 없어야 하고,
   * 왜 못 고르는지가 같은 자리에 있어야 한다.
   */
  it('기간이 겹치지 않는 여정은 보이되 고를 수 없다', () => {
    const wrapper = mountSheet({ itemStartDate: '2026-09-04', itemEndDate: '2026-09-06' })
    const button = journeyButton(wrapper)

    expect(button).toBeDefined()
    expect(button?.attributes('aria-disabled')).toBe('true')
    expect(button?.text()).toContain('Outside this event’s dates')
  })

  /*
   * `disabled`를 쓰면 탭 순서에서 빠지고 스크린 리더가 건너뛴다 — 감추지 않기로 한
   * 이유가 그들에게만 뒤집힌다. 포커스는 받되 선택만 막혀야 한다.
   */
  it('고를 수 없는 여정도 포커스를 받고, 눌러도 선택되지 않는다', async () => {
    const wrapper = mountSheet({ itemStartDate: '2026-09-04', itemEndDate: '2026-09-06' })
    const button = journeyButton(wrapper)

    expect(button?.attributes('disabled')).toBeUndefined()

    await button?.trigger('click')

    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('겹치는 여정에는 사유를 붙이지 않는다', () => {
    const wrapper = mountSheet({})
    const button = journeyButton(wrapper)

    expect(button?.attributes('aria-disabled')).toBe('false')
    expect(button?.text()).not.toContain('Outside this event’s dates')
  })

  it('고를 수 있는 여정은 눌리면 선택된다', async () => {
    const wrapper = mountSheet({})

    await journeyButton(wrapper)?.trigger('click')

    expect(wrapper.emitted('select')).toHaveLength(1)
  })

  it('하루만 맞닿아도 고를 수 있다', () => {
    const wrapper = mountSheet({ itemStartDate: '2026-04-01', itemEndDate: '2026-04-30' })

    expect(journeyButton(wrapper)?.attributes('aria-disabled')).toBe('false')
  })

  it('여정이 하나도 없으면 만들러 가는 버튼을 보인다', async () => {
    const wrapper = mountSheet({ journeys: [] })

    expect(wrapper.text()).toContain('Create a journey before adding this event.')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create a journey')
      ?.trigger('click')

    expect(wrapper.emitted('createJourney')).toHaveLength(1)
  })

  /*
   * 목록이 비어 있을 때만이 아니다. 고를 수 있는 여정이 하나도 없는 것도 사용자에게는
   * 담을 여정이 없는 것과 똑같다.
   */
  it('겹치는 여정이 하나도 없어도 같은 자리에서 만들러 갈 수 있다', async () => {
    const wrapper = mountSheet({ itemStartDate: '2026-09-04', itemEndDate: '2026-09-06' })

    expect(wrapper.text()).toContain('None of your journeys overlap this event’s dates.')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create a journey')
      ?.trigger('click')

    expect(wrapper.emitted('createJourney')).toHaveLength(1)
  })

  it('고를 수 있는 여정이 있으면 만들기 버튼을 보이지 않는다', () => {
    const wrapper = mountSheet({})

    expect(wrapper.text()).not.toContain('Create a journey')
  })

  it('읽는 중에는 목록도 만들기 버튼도 보이지 않는다', () => {
    const wrapper = mountSheet({ journeys: [], loading: true })

    expect(wrapper.text()).toContain('Loading your journeys…')
    expect(wrapper.text()).not.toContain('Create a journey')
  })
})
