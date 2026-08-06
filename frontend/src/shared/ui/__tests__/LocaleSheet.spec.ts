import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import LocaleSheet from '../LocaleSheet.vue'

function mountSheet(modelValue = 'en') {
  return mount(LocaleSheet, {
    props: { modelValue: modelValue as 'en', title: 'Language', hint: 'Applies right away.' },
  })
}

describe('LocaleSheet', () => {
  it('offers every supported locale', () => {
    expect(mountSheet().findAll('[role="radio"]')).toHaveLength(5)
  })

  /*
   * 언어 이름을 영어로만 적으면 정작 그 언어 사용자가 자기 언어를 찾지 못한다.
   * 자국어 표기가 먼저 오고 영문 표기는 보조다.
   */
  it('labels each locale in its own language', () => {
    const text = mountSheet().text()

    for (const native of ['English', '日本語', '简体中文', '繁體中文', 'Tiếng Việt']) {
      expect(text).toContain(native)
    }
  })

  it('marks only the active locale as checked', () => {
    const checked = mountSheet('ja')
      .findAll('[role="radio"]')
      .map((option) => option.attributes('aria-checked'))

    expect(checked.filter((value) => value === 'true')).toHaveLength(1)
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
