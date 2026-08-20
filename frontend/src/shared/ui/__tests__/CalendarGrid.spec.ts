import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import CalendarGrid from '../CalendarGrid.vue'

/** 고정된 오늘. 기본으로 열리는 달이 실행 시점에 따라 달라지면 안 된다. */
const NOW = new Date('2026-07-15T00:00:00Z')

function mountCalendar(props: Record<string, unknown> = {}) {
  return mount(CalendarGrid, { props, global: { plugins: [i18n] } })
}

describe('CalendarGrid', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  /**
   * 네이티브 <input type="date">를 쓰지 않는 이유가 이것이다. 그 입력은 브라우저 UI
   * 언어를 따라 한국어 브라우저에서 `연도. 월. 일.`로 나온다.
   */
  it('labels the month in the app locale, not the browser locale', () => {
    expect(mountCalendar().text()).toContain('July 2026')
  })

  it('opens on the selected month rather than today', () => {
    expect(mountCalendar({ selected: '2026-11-03' }).text()).toContain('November 2026')
  })

  it('moves between months', async () => {
    const wrapper = mountCalendar()

    await wrapper.get('button[aria-label="Next month"]').trigger('click')
    expect(wrapper.text()).toContain('August 2026')

    await wrapper.get('button[aria-label="Previous month"]').trigger('click')
    await wrapper.get('button[aria-label="Previous month"]').trigger('click')
    expect(wrapper.text()).toContain('June 2026')
  })

  it('emits the date that was chosen', async () => {
    const wrapper = mountCalendar()

    await wrapper.get('button[aria-label="Select July 1, 2026"]').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2026-07-01']])
  })

  /** 앞뒤 달에서 채운 칸은 눌리지 않는다. 달을 옮겨서 고르게 한다. */
  it('disables days outside the visible month', async () => {
    const wrapper = mountCalendar()
    const outside = wrapper.get('button[aria-label="Select June 30, 2026"]')

    expect(outside.attributes('disabled')).toBeDefined()

    await outside.trigger('click')

    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('respects the caller decision about which dates are allowed', async () => {
    const wrapper = mountCalendar({ isDateAllowed: (date: string) => date >= '2026-07-10' })

    expect(
      wrapper.get('button[aria-label="Select July 1, 2026"]').attributes('disabled'),
    ).toBeDefined()

    await wrapper.get('button[aria-label="Select July 10, 2026"]').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['2026-07-10']])
  })

  it('marks both ends of a range as selected', () => {
    const wrapper = mountCalendar({ rangeStart: '2026-07-05', rangeEnd: '2026-07-08' })

    const pressed = wrapper
      .findAll('button[aria-pressed="true"]')
      .map((button) => button.attributes('aria-label'))

    expect(pressed).toEqual(['Select July 5, 2026', 'Select July 8, 2026'])
  })

  /** 기간 선택 화면은 날짜마다 이름을 붙이지 않는다. 셀 42개가 모두 읽히면 시끄럽다. */
  it('can leave the day cells unlabelled', () => {
    const wrapper = mountCalendar({ labelDates: false })

    expect(wrapper.find('button[aria-label^="Select"]').exists()).toBe(false)
    // 달 이동 버튼은 이름이 남아야 한다. 날짜 셀만 조용해지는 것이 의도다.
    expect(wrapper.find('button[aria-label="Next month"]').exists()).toBe(true)
  })
})
