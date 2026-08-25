import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import TicketStamp from '../TicketStamp.vue'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('TicketStamp', () => {
  /*
   * 도장은 장식이다. 상태를 읽히는 몫은 옆의 배지가 지므로, 여기까지 읽히면 같은 말이
   * 두 번 나온다.
   */
  it('stays out of the accessibility tree', () => {
    const wrapper = mount(TicketStamp, { props: { label: 'Ended' } })

    expect(wrapper.get('span').attributes('aria-hidden')).toBe('true')
    expect(wrapper.text()).toBe('Ended')
  })

  // 대문자는 CSS가 강제한다. 부르는 쪽이 대문자로 넘긴다고 가정하지 않는다.
  it('uppercases through CSS rather than the passed text', () => {
    const wrapper = mount(TicketStamp, { props: { label: 'On trip' } })

    expect(wrapper.get('span').classes()).toContain('uppercase')
    expect(wrapper.text()).toBe('On trip')
  })

  /*
   * 🔴 문구 길이가 로케일마다 크게 다르다. `Ended`(5자)가 vi에서 `Đã kết thúc`(11자)이
   * 되면 12px 그대로는 64px 원을 뚫고 나간다 — 실제로 그랬다(#522에서 발견).
   *
   * `v-fit-text`는 전역 등록이 아니라 `<script setup>`의 `vFitText` import로 걸린다.
   * 빠뜨려도 lint·type-check·렌더가 전부 통과하고 글자 맞춤만 조용히 죽으므로,
   * 미해결 디렉티브 경고를 직접 감시한다. `ScreenHeader`에서 한 번 빠뜨린 자리다(#489).
   */
  it('resolves the fit-text directive', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

    mount(TicketStamp, { props: { label: 'Đã kết thúc' } })

    const messages = warn.mock.calls.map((call) => String(call[0])).join('\n')
    expect(messages).not.toContain('Failed to resolve directive')
  })

  // 원의 곡선 몫을 좌우 여백으로 미리 비운다. `v-fit-text`는 사각 경계로만 재기 때문이다.
  it('keeps side padding so the curve does not clip long words', () => {
    const wrapper = mount(TicketStamp, { props: { label: 'Đã kết thúc' } })

    expect(wrapper.get('span').classes()).toContain('px-2')
  })
})
