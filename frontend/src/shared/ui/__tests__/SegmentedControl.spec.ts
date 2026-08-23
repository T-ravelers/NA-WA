import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import SegmentedControl from '../SegmentedControl.vue'

const OPTIONS = [
  { value: 'ongoing', label: 'Ongoing' },
  { value: 'past', label: 'Past' },
]

function mountControl(modelValue = 'ongoing') {
  return mount(SegmentedControl, {
    props: { modelValue, options: OPTIONS, label: 'Journey filter' },
  })
}

describe('SegmentedControl', () => {
  it('renders one control per option', () => {
    expect(mountControl().findAll('button')).toHaveLength(2)
  })

  it('is announced as a radio group with a name', () => {
    const group = mountControl().get('[role="radiogroup"]')

    expect(group.attributes('aria-label')).toBe('Journey filter')
  })

  it('marks only the selected option as checked', () => {
    const buttons = mountControl('past').findAll('button')

    expect(buttons.map((b) => b.attributes('aria-checked'))).toEqual(['false', 'true'])
  })

  it('fills the selected option so it reads as chosen without relying on aria alone', () => {
    const buttons = mountControl('ongoing').findAll('button')

    expect(buttons[0]?.classes()).toContain('bg-paper-fill')
    expect(buttons[1]?.classes()).toContain('bg-transparent')
  })

  it('emits the chosen value', async () => {
    const wrapper = mountControl('ongoing')

    await wrapper.findAll('button')[1]?.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['past']])
  })

  // 이미 선택된 것을 눌러도 화면이 흔들리지 않아야 한다.
  it('still emits when the already selected option is pressed', async () => {
    const wrapper = mountControl('ongoing')

    await wrapper.findAll('button')[0]?.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['ongoing']])
  })

  /*
   * 라디오 그룹은 탭 스톱이 하나이고 안에서는 화살표로 옮겨 다닌다(#305). 선택지가 둘일
   * 때는 비용이 드러나지 않지만, 같은 관행이 QR 결제의 칩 일곱 개로 번지면서 결제
   * 버튼까지 탭을 일곱 번 더 눌러야 하는 상태가 됐다.
   */
  it('keeps only the selected option in the tab order', () => {
    const buttons = mountControl('past').findAll('button')

    expect(buttons.map((b) => b.attributes('tabindex'))).toEqual(['-1', '0'])
  })

  it.each([
    ['ArrowRight', 'ongoing', 'past'],
    ['ArrowDown', 'ongoing', 'past'],
    ['ArrowLeft', 'past', 'ongoing'],
    ['ArrowUp', 'past', 'ongoing'],
    ['Home', 'past', 'ongoing'],
    ['End', 'ongoing', 'past'],
  ])('moves the selection with %s', async (key, from, expected) => {
    const wrapper = mountControl(from)

    await wrapper.get('[role="radiogroup"]').trigger('keydown', { key })

    expect(wrapper.emitted('update:modelValue')).toEqual([[expected]])
  })

  it('wraps around at both ends', async () => {
    const forward = mountControl('past')
    await forward.get('[role="radiogroup"]').trigger('keydown', { key: 'ArrowRight' })
    expect(forward.emitted('update:modelValue')).toEqual([['ongoing']])

    const backward = mountControl('ongoing')
    await backward.get('[role="radiogroup"]').trigger('keydown', { key: 'ArrowLeft' })
    expect(backward.emitted('update:modelValue')).toEqual([['past']])
  })

  it('leaves other keys to the page', async () => {
    const wrapper = mountControl('ongoing')

    await wrapper.get('[role="radiogroup"]').trigger('keydown', { key: 'Tab' })
    await wrapper.get('[role="radiogroup"]').trigger('keydown', { key: 'a' })

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  /*
   * `Cmd + ←`(macOS)·`Alt + ←`(Windows·Linux)는 브라우저 뒤로 가기다. 그룹이 가로채면
   * 뒤로 가기가 막히고 대신 선택이 바뀐다 — 누른 사람이 의도한 것과 정반대다.
   */
  it.each([['metaKey'], ['ctrlKey'], ['altKey'], ['shiftKey']])(
    'leaves %s + arrow to the browser',
    async (modifier) => {
      const wrapper = mountControl('ongoing')
      const preventDefault = vi.fn()

      await wrapper
        .get('[role="radiogroup"]')
        .trigger('keydown', { key: 'ArrowLeft', [modifier]: true, preventDefault })

      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
      expect(preventDefault).not.toHaveBeenCalled()
    },
  )

  /*
   * 초점이 **선택과 함께** 움직여야 라디오 그룹이다. 선택만 바뀌고 초점이 남으면 다음
   * 화살표가 엉뚱한 자리에서 출발한다.
   *
   * 이 단언이 없으면 초점 이동이 통째로 깨져도 조용하다 — 실제로 `CSS.escape`가 jsdom에
   * 없어 핸들러가 터졌는데, 선택은 이미 바뀐 뒤라 다른 단언은 전부 통과했다.
   */
  it('moves focus onto the newly selected option', async () => {
    const wrapper = mount(SegmentedControl, {
      props: { modelValue: 'ongoing', options: OPTIONS, label: 'Journey filter' },
      attachTo: document.body,
    })

    await wrapper.get('[role="radiogroup"]').trigger('keydown', { key: 'ArrowRight' })

    expect(document.activeElement).toBe(wrapper.get('[data-value="past"]').element)

    wrapper.unmount()
  })

  /* 화살표가 화면까지 스크롤하면 그룹 안에서 이동만 하려던 조작이 페이지를 흔든다. */
  it('stops the arrow key from also scrolling the page', async () => {
    const wrapper = mountControl('ongoing')
    const preventDefault = vi.fn()

    await wrapper
      .get('[role="radiogroup"]')
      .trigger('keydown', { key: 'ArrowRight', preventDefault })

    expect(preventDefault).toHaveBeenCalled()
  })
})
