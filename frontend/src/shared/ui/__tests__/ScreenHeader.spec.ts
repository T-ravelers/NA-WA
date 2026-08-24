import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import ScreenHeader from '../ScreenHeader.vue'

function mountHeader(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(ScreenHeader, {
    props: { variant: 'back', title: 'Wallet', backLabel: 'Back', ...props },
    slots,
  })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('ScreenHeader', () => {
  it('renders a back button that emits back', async () => {
    const wrapper = mountHeader()

    const button = wrapper.get('button')
    expect(button.attributes('aria-label')).toBe('Back')
    /*
     * 스크린샷 러너와 뷰 테스트가 이 훅으로 뒤로가기를 찾는다. 접근 이름으로 찾으면
     * 번역된 로케일에서 못 찾아 화면을 통째로 건너뛴다(#489에서 실제로 5장이 빠졌다).
     */
    expect(button.attributes('data-testid')).toBe('screen-back')

    await button.trigger('click')
    expect(wrapper.emitted('back')).toHaveLength(1)
  })

  it('has no back button on a root screen', () => {
    const wrapper = mountHeader({ variant: 'root', backLabel: undefined })

    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.get('h1').text()).toBe('Wallet')
  })

  /*
   * 제목 조형이 되돌아가는 것을 여기서 잡는다. 뷰 21개가 각자 헤더를 쓰던 때 제목이
   * `text-section-header`(22px)와 `text-title`(18px 중앙정렬)로 갈렸고, `uppercase`가
   * 빠진 곳과 `font-bold`를 중복으로 붙인 곳이 함께 있었다(#489).
   */
  it('keeps one title shape', () => {
    const classes = mountHeader().get('h1').classes()

    expect(classes).toContain('font-display')
    expect(classes).toContain('text-screen-title')
    expect(classes).toContain('uppercase')
    expect(classes).toContain('text-ink-display')
    // `--text-screen-title--font-weight`가 이미 700이다.
    expect(classes).not.toContain('font-bold')
    // 좁은 폭에서 글자를 줄이고, 하한에서 말줄임으로 넘긴다(#361).
    expect(classes).toContain('truncate')
  })

  /*
   * `v-fit-text`는 전역 등록이 아니라 `<script setup>`의 `vFitText` import로 걸린다.
   * 빠뜨려도 lint·type-check·렌더가 전부 통과하고 글자 축소만 조용히 죽으므로,
   * 미해결 디렉티브 경고를 직접 감시한다. 실제로 한 번 빠뜨린 자리다.
   */
  it('resolves the fit-text directive', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

    mountHeader()

    const messages = warn.mock.calls.map((call) => String(call[0])).join('\n')
    expect(messages).not.toContain('Failed to resolve directive')
  })

  // 헤더를 선으로 본문과 나누지 않는다. 간격은 화면 컨테이너의 `gap-8`이 준다.
  it('does not separate itself with a rule', () => {
    expect(mountHeader().get('header').html()).not.toContain('border-b')
  })

  it('renders an action next to the title', () => {
    const wrapper = mountHeader({ variant: 'root' }, { action: '<button id="add">Add</button>' })

    expect(wrapper.find('#add').exists()).toBe(true)
  })

  /*
   * 보조 문장이 붙는 화면은 제목과 문장이 한 덩어리로 읽혀야 한다. 컨테이너의 32px이
   * 그대로 걸리면 둘이 따로 떨어져 보인다.
   */
  it('stacks a description under the title only when one is given', () => {
    expect(mountHeader().get('header').classes()).not.toContain('flex-col')

    const withDescription = mountHeader({ variant: 'root' }, { description: 'Your trips' })
    expect(withDescription.get('header').classes()).toContain('flex-col')
    expect(withDescription.get('p').text()).toBe('Your trips')
  })
})
