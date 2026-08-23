import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { SUPPORTED_LOCALES } from '@/shared/i18n/locales'

import LocaleSheet from '../LocaleSheet.vue'

function mountSheet(modelValue = 'en') {
  return mount(LocaleSheet, {
    props: { modelValue: modelValue as 'en', title: 'Language', hint: 'Applies right away.' },
  })
}

describe('LocaleSheet', () => {
  it('offers every supported locale', () => {
    expect(mountSheet().findAll('[role="radio"]')).toHaveLength(SUPPORTED_LOCALES.length)
  })

  /*
   * 언어 이름을 영어로만 적으면 정작 그 언어 사용자가 자기 언어를 찾지 못한다.
   * 자국어 표기가 먼저 오고 영문 표기는 보조다.
   */
  it('labels each locale in its own language', () => {
    const text = mountSheet().text()

    for (const native of ['English', '日本語', '繁體中文', 'Tiếng Việt']) {
      expect(text).toContain(native)
    }
  })

  it('marks only the active locale as checked', () => {
    const checked = mountSheet('ja')
      .findAll('[role="radio"]')
      .map((option) => option.attributes('aria-checked'))

    expect(checked.filter((value) => value === 'true')).toHaveLength(1)
  })

  /*
   * `role="radio"`가 `radiogroup` 안에 있어야 스크린 리더가 「4개 중 2번째」를 읽는다.
   * 행 배경을 통일해 선택 신호가 체크 원과 `aria-checked` 둘로 줄었으므로 더 그렇다.
   */
  it('groups the options so their position is announced', () => {
    const wrapper = mountSheet()
    const group = wrapper.get('[role="radiogroup"]')

    expect(group.findAll('[role="radio"]')).toHaveLength(SUPPORTED_LOCALES.length)

    const titleId = wrapper.get('h2').attributes('id')
    expect(titleId).toBeTruthy()
    expect(group.attributes('aria-labelledby')).toBe(titleId)
  })

  /*
   * 시안(V2 `2297:2022`)은 선택 여부와 무관하게 모든 행을 같은 면으로 그린다. 고른 것은
   * 체크 원이 말한다. 선택행에만 색을 다시 넣으면 이 단언이 잡는다 — 그러지 않으면
   * 스크린샷 러너가 유일한 방어선이 된다.
   */
  it('draws every row on the same surface', () => {
    const surfaces = mountSheet('ja')
      .findAll('[role="radio"]')
      .map((option) => option.classes().filter((name) => name.startsWith('bg-')))

    expect(surfaces).toEqual(
      Array.from({ length: SUPPORTED_LOCALES.length }, () => ['bg-surface-1']),
    )
  })

  /*
   * 라디오 그룹은 탭 스톱이 그룹당 하나이고 화살표로 옮겨 다니는 것이 전제다(#305/#433).
   * 그러지 않으면 선택지 네 개가 전부 탭 순서에 들어가, 시트를 지나가는 데 탭을 세 번 더
   * 눌러야 한다. `SegmentedControl`과 같은 `useRovingRadioGroup` 규약을 쓴다.
   */
  it('keeps one tab stop and moves with the arrow keys', async () => {
    const wrapper = mountSheet('ja')
    const tabindexes = wrapper
      .findAll('[role="radio"]')
      .map((option) => option.attributes('tabindex'))

    expect(tabindexes.filter((value) => value === '0')).toHaveLength(1)
    expect(tabindexes[SUPPORTED_LOCALES.indexOf('ja')]).toBe('0')

    await wrapper.get('[role="radiogroup"]').trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.emitted('update:modelValue')).toEqual([[SUPPORTED_LOCALES[2]]])
  })

  it('is announced as a modal dialog with a name', () => {
    const dialog = mountSheet().get('[role="dialog"]')

    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.attributes('aria-label')).toBe('Language')
  })

  it('emits the chosen locale', async () => {
    const wrapper = mountSheet('en')

    await wrapper.findAll('[role="radio"]')[1]?.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['ja']])
  })

  it('closes when the area outside the sheet is pressed', async () => {
    const wrapper = mountSheet()

    await wrapper.get('[aria-hidden="true"].absolute').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
