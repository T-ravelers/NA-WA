import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentJourneyDateSheet from '../AppointmentJourneyDateSheet.vue'

const props = {
  journeyTitle: 'Seoul Foodie Week',
  startDate: '2026-08-01',
  endDate: '2026-08-31',
  initialDate: '2026-08-08',
}

describe('AppointmentJourneyDateSheet', () => {
  // 여정 기간(2026-08-01~31)이 전부 미래로 보이도록 오늘을 그 이전으로 고정한다.
  // 지난 날짜 제외 동작은 별도 테스트에서 오늘을 여정 기간 안으로 옮겨 확인한다.
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-01T00:00:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('emits the selected date when confirmed', async () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    const applyButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Continue with'))

    await applyButton?.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['2026-08-08']])
  })

  it('emits close when the scrim is pressed', async () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    await wrapper.get('button[aria-label="Close date picker"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('emits close when the back arrow is pressed', async () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props,
    })

    await wrapper.get('button[aria-label="Go back"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('does not allow dates outside the journey range', () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props: { ...props, startDate: '2026-08-05', endDate: '2026-08-10' },
    })

    const dayOutsideRange = wrapper.get('button[aria-label="Select August 1, 2026"]')

    expect(dayOutsideRange.attributes('disabled')).toBeDefined()
  })

  it('does not allow a past date within an in-progress journey', () => {
    // 여정 시작일이 과거인 진행 중 여정이다(생성 시점엔 허용되는 조합). 오늘
    // 이전 날짜를 고르면 백엔드의 "활동 시작이 현재 이후" 검증에 항상 걸리므로
    // 여기서부터 막아야 한다.
    vi.setSystemTime(new Date('2026-08-19T12:00:00'))
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props: { ...props, initialDate: '2026-08-08' },
    })

    const pastDay = wrapper.get('button[aria-label="Select August 15, 2026"]')
    const todayOrLater = wrapper.get('button[aria-label="Select August 19, 2026"]')

    expect(pastDay.attributes('disabled')).toBeDefined()
    expect(todayOrLater.attributes('disabled')).toBeUndefined()
  })

  it('shows the duplicate-combination error and keeps the sheet open', () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props: {
        ...props,
        errorMessage: 'This activity is already linked to an appointment on this day.',
      },
    })

    expect(wrapper.text()).toContain(
      'This activity is already linked to an appointment on this day.',
    )
  })

  it('disables the apply button while checking the date', () => {
    const wrapper = mount(AppointmentJourneyDateSheet, {
      global: { plugins: [i18n] },
      props: { ...props, loading: true },
    })

    const applyButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Continue with'))

    expect(applyButton?.attributes('disabled')).toBeDefined()
  })
})
