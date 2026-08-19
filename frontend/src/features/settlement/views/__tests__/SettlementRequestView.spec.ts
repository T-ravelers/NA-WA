import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import type { SettlementCandidate } from '../../model/settlement'
import { settlementKeys } from '../../model/settlementQueries'
import SettlementRequestView from '../SettlementRequestView.vue'

const { getCandidates } = vi.hoisted(() => ({ getCandidates: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getCandidates } }))

function candidate(overrides: Partial<SettlementCandidate> = {}): SettlementCandidate {
  return {
    transferId: '7',
    appointmentId: '9',
    payerAppointmentMemberId: '12',
    journeyName: 'Seoul',
    gatheringName: 'Dinner',
    merchantName: 'Dinner',
    amount: '25.00',
    paidAt: 'Aug 12, 2026, 7:30 PM',
    payerName: 'Alex',
    participants: [
      { id: '12', name: 'Alex', initials: 'AL' },
      { id: '19', name: 'Mina', initials: 'MI' },
    ],
    ...overrides,
  }
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/settlements/new', name: 'settlement-new', component: SettlementRequestView },
      { path: '/settlements', name: 'settlements', component: { template: '<div />' } },
      {
        path: '/settlements/:settlementId/requested',
        name: 'settlement-requested',
        component: { template: '<div />' },
      },
    ],
  })
}

async function mountRequest() {
  const router = createTestRouter()
  await router.push('/settlements/new')
  await router.isReady()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(SettlementRequestView, {
    global: { plugins: [i18n, router, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()

  /** 창 포커스가 돌아왔을 때처럼 후보 목록만 다시 조회된 상황을 만든다. */
  async function refetchCandidates(): Promise<void> {
    // 조회 실패도 이 화면의 검증 대상이라 여기서 삼킨다. 실패 자체는 화면으로 확인한다.
    await queryClient
      .invalidateQueries({ queryKey: settlementKeys.candidates() })
      .catch(() => undefined)
    await flushPromises()
  }

  return { wrapper, refetchCandidates }
}

describe('SettlementRequestView', () => {
  // 화살표 함수가 값을 돌려주면 vitest가 그것을 뒷정리 콜백으로 보고 테스트가 끝난 뒤 부른다.
  // mockResolvedValue는 mock 자신을 돌려주므로, 블록으로 감싸지 않으면 여기서 조회가 한 번 더
  // 불려 거부된 약속이 처리되지 않은 채 남는다.
  beforeEach(() => {
    getCandidates.mockReset().mockResolvedValue([candidate()])
  })

  it('shows the empty state when the first load finds no payments', async () => {
    getCandidates.mockResolvedValue([])
    const { wrapper } = await mountRequest()

    expect(wrapper.text()).toContain('No payments available')
    expect(wrapper.find('[data-journey-key]').exists()).toBe(false)
  })

  it('shows the error state when the first load fails, then opens the wizard on retry', async () => {
    getCandidates.mockRejectedValue(new Error('network'))
    const { wrapper } = await mountRequest()

    expect(wrapper.text()).toContain('We could not complete this split action')
    expect(wrapper.find('[data-journey-key]').exists()).toBe(false)

    // 최초 실패는 지금처럼 오류 화면을 유지하고, 재시도가 성공해야 래치가 잠긴다.
    getCandidates.mockResolvedValue([candidate()])
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-journey-key="Seoul"]').exists()).toBe(true)
  })

  it('keeps the wizard mounted when a later refetch comes back empty', async () => {
    const { wrapper, refetchCandidates } = await mountRequest()
    await wrapper.get('[data-journey-key="Seoul"]').trigger('click')
    await wrapper.get('[data-appointment-id="9"]').trigger('click')
    await wrapper.get('[data-payment-id="7"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')

    getCandidates.mockResolvedValue([])
    await refetchCandidates()

    // 위저드가 살아 있어야 사용자가 왜 고를 게 없어졌는지 읽고 되돌아갈 수 있다. 부모의
    // 전체 화면 빈 상태와 문구가 겹치므로 위저드에만 있는 안내로 구분한다.
    expect(wrapper.text()).toContain('no longer available')
  })

  it('keeps the wizard mounted when a later refetch fails', async () => {
    const { wrapper, refetchCandidates } = await mountRequest()
    await wrapper.get('[data-journey-key="Seoul"]').trigger('click')

    getCandidates.mockRejectedValue(new Error('network'))
    await refetchCandidates()

    expect(wrapper.find('[data-appointment-id="9"]').exists()).toBe(true)
  })
})
