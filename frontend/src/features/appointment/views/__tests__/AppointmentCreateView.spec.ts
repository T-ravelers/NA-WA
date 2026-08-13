import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

const AppointmentCreateView = (await import('../AppointmentCreateView.vue')).default

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (button === undefined) throw new Error(`Button not found: ${text}`)
  return button
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/appointments/new',
        name: 'appointment-create',
        component: AppointmentCreateView,
      },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div>Detail</div>' },
      },
      {
        path: '/appointments',
        name: 'appointment-list',
        component: { template: '<div>List</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/appointments/new?itemId=42&itemType=EVENT')
  await router.isReady()

  const wrapper = mount(AppointmentCreateView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function fillAndConfirm(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper
    .find('input[placeholder="e.g. Seongsu K-Beauty Tour"]')
    .setValue('Seongsu K-Beauty Tour')
  await wrapper.get('form').trigger('submit')

  await wrapper.find('input[inputmode="numeric"]').setValue('10000')
  await wrapper.find('input[placeholder="e.g. Seongsu Beauty Lab"]').setValue('Seongsu Beauty Lab')
  await wrapper.get('form').trigger('submit')

  await wrapper.find('input[type="datetime-local"]').setValue('2026-08-08T18:30')
  await wrapper.findAll('input[type="datetime-local"]')[1]?.setValue('2026-08-08T22:00')
  await wrapper.findAll('input[type="datetime-local"]')[2]?.setValue('2026-08-08T17:30')
  await wrapper.get('form').trigger('submit')
  await buttonByText(wrapper, 'Confirm').trigger('click')
}

describe('AppointmentCreateView', () => {
  it('keeps creation confirmation disabled until payment integration is available', async () => {
    const { wrapper, router } = await mountView()

    await fillAndConfirm(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('Payment integration is required')
    expect(buttonByText(wrapper, 'Confirm').attributes('disabled')).toBeDefined()
    expect(router.currentRoute.value.name).toBe('appointment-create')
  })
})
