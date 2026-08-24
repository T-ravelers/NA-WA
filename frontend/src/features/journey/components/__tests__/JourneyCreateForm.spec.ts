import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import JourneyCreateForm from '../JourneyCreateForm.vue'
import JourneyDateRangePicker from '../JourneyDateRangePicker.vue'

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))

  if (button === undefined) {
    throw new Error(`Button not found: ${text}`)
  }

  return button
}

async function fillRequiredFields(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.get('input[type="text"]').setValue('Seoul Foodie Week')
  const picker = wrapper.findComponent(JourneyDateRangePicker)

  picker.vm.$emit('update:startDate', '2026-08-10')
  picker.vm.$emit('update:endDate', '2026-08-12')
  await wrapper.vm.$nextTick()
}

describe('JourneyCreateForm', () => {
  /*
   * 담을 항목의 운영 기간을 받아 기간 입력칸을 미리 채운다. prop을 받기만 하고 입력칸에
   * 넣지 않으면, 그 사람은 무엇과 겹쳐야 하는지 모른 채 폼을 채우고 또 안 겹치는 여정을
   * 만들어 같은 자리로 돌아온다.
   */
  it('넘겨받은 항목 기간으로 기간 입력칸이 채워진 채 열린다', () => {
    const wrapper = mount(JourneyCreateForm, {
      props: { initialStartDate: '2026-08-10', initialEndDate: '2026-08-12' },
      global: { plugins: [i18n] },
    })

    const picker = wrapper.getComponent(JourneyDateRangePicker)

    expect(picker.props('startDate')).toBe('2026-08-10')
    expect(picker.props('endDate')).toBe('2026-08-12')
  })

  it('항목 기간이 없으면 기간 입력칸은 빈 채로 열린다', () => {
    const wrapper = mount(JourneyCreateForm, { global: { plugins: [i18n] } })

    const picker = wrapper.getComponent(JourneyDateRangePicker)

    expect(picker.props('startDate')).toBe('')
    expect(picker.props('endDate')).toBe('')
  })

  it('keeps the user on step one and exposes validation errors', async () => {
    const wrapper = mount(JourneyCreateForm, { global: { plugins: [i18n] } })

    await buttonByText(wrapper, 'Next').trigger('click')

    expect(wrapper.text()).toContain('Enter a trip name.')
    expect(wrapper.text()).toContain('Step 1 of 2')
  })

  it('submits no regions and the selected companion code', async () => {
    const wrapper = mount(JourneyCreateForm, { global: { plugins: [i18n] } })
    await fillRequiredFields(wrapper)

    await buttonByText(wrapper, 'Next').trigger('click')
    await buttonByText(wrapper, 'Small group travel').trigger('click')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      title: 'Seoul Foodie Week',
      startDate: '2026-08-10',
      endDate: '2026-08-12',
      budgetAmount: null,
      companionPreference: '2-4',
      regions: [],
    })
    expect(wrapper.text()).not.toContain('Add region')
  })

  it('disables the form and marks it busy while creation is pending', () => {
    const wrapper = mount(JourneyCreateForm, {
      props: { pending: true },
      global: { plugins: [i18n] },
    })

    expect(wrapper.get('form').attributes('aria-busy')).toBe('true')
    expect(wrapper.get('fieldset').attributes()).toHaveProperty('disabled')
  })

  it('uses the shared custom range picker instead of native date inputs', () => {
    const wrapper = mount(JourneyCreateForm, { global: { plugins: [i18n] } })

    expect(wrapper.findAll('input[type="date"]')).toHaveLength(0)
    expect(wrapper.findComponent(JourneyDateRangePicker).exists()).toBe(true)
  })
})
