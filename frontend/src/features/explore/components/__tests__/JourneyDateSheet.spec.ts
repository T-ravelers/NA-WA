import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import JourneyDateSheet from '../JourneyDateSheet.vue'

const props = {
  itemTitle: 'DDP Architecture Tour',
  itemLocation: 'Seoul · Dongdaemun·DDP',
  startDate: '2026-08-01',
  endDate: '2026-08-31',
  initialDate: '2026-08-08',
}

describe('JourneyDateSheet', () => {
  it('emits the selected date when the journey action is confirmed', async () => {
    const wrapper = mount(JourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    const applyButton = wrapper.findAll('button').find((button) => button.text().includes('Add to'))

    await applyButton?.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['2026-08-08']])
  })

  it('closes when the scrim is pressed', async () => {
    const wrapper = mount(JourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    await wrapper.get('button[aria-label="Close date picker"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('disables confirmation while journey item registration is unavailable', () => {
    const wrapper = mount(JourneyDateSheet, {
      global: { plugins: [i18n] },
      props: { ...props, confirmDisabled: true },
    })

    const applyButton = wrapper.findAll('button').find((button) => button.text().includes('Add to'))

    expect(applyButton?.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Adding items to a journey will be available soon.')
  })

  /*
   * 부모가 넘긴 구간은 이미 **항목 운영 기간 ∩ 여정 기간**이다. 예전에는
   * `isPermanent`가 참이면 이 검사를 통째로 건너뛰어 여정 기간 밖도 열렸다.
   */
  it('넘겨받은 구간 밖은 고를 수 없다', () => {
    const wrapper = mount(JourneyDateSheet, {
      global: { plugins: [i18n] },
      props: { ...props, startDate: '2026-08-10', endDate: '2026-08-12' },
    })

    const dayCell = (label: string) =>
      wrapper.findAll('button').find((button) => button.text().trim() === label)

    expect(dayCell('11')?.attributes('disabled')).toBeUndefined()
    expect(dayCell('9')?.attributes('disabled')).toBeDefined()
    expect(dayCell('13')?.attributes('disabled')).toBeDefined()
  })

  it('구간 밖 initialDate 대신 구간 시작일을 고른다', async () => {
    const wrapper = mount(JourneyDateSheet, {
      global: { plugins: [i18n] },
      props: {
        ...props,
        startDate: '2026-08-10',
        endDate: '2026-08-12',
        initialDate: '2026-08-01',
      },
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Add to'))
      ?.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['2026-08-10']])
  })
})
