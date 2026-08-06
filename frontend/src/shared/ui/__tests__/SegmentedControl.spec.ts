import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

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
})
