import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import JourneyCreateForm from '../JourneyCreateForm.vue'

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))

  if (button === undefined) {
    throw new Error(`Button not found: ${text}`)
  }

  return button
}

async function fillRequiredFields(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.get('input[type="text"]').setValue('Seoul Foodie Week')
  const dateInputs = wrapper.findAll('input[type="date"]')

  await dateInputs[0]?.setValue('2026-08-10')
  await dateInputs[1]?.setValue('2026-08-12')
}

describe('JourneyCreateForm', () => {
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

  it('uses unique shared input ids and constrains the end date from the selected start date', async () => {
    const wrapper = mount(JourneyCreateForm, { global: { plugins: [i18n] } })
    const dateInputs = wrapper.findAll('input[type="date"]')

    await dateInputs[0]?.setValue('2026-08-10')

    expect(dateInputs).toHaveLength(2)
    expect(dateInputs[0]?.attributes('id')).not.toBe(dateInputs[1]?.attributes('id'))
    expect(dateInputs[1]?.attributes('min')).toBe('2026-08-10')
  })
})
