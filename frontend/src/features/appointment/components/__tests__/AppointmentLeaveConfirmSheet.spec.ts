import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import AppointmentLeaveConfirmSheet from '../AppointmentLeaveConfirmSheet.vue'

function mountSheet(noShow = false) {
  return mount(AppointmentLeaveConfirmSheet, {
    props: {
      appointmentName: 'Seongsu Night Walk',
      depositAmount: '10000',
      noShow,
    },
    global: { plugins: [i18n] },
  })
}

describe('AppointmentLeaveConfirmSheet', () => {
  // 환급 탈퇴는 경고가 아니므로 문구는 중립으로 둔다(#371). 다만 확정 버튼까지
  // paper-fill로 두면 모달의 밝은 바탕과 대비가 1.02라 버튼이 보이지 않아,
  // 보증금이 오가는 확정에 써 온 노랑으로 바꿨다.
  it('keeps the refundable leave copy neutral and its action visible', () => {
    const wrapper = mountSheet()
    const refundCopy = wrapper
      .findAll('p')
      .find((paragraph) => paragraph.text().includes('refunded'))
    const confirmButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Leave group')

    expect(refundCopy?.classes()).toContain('text-on-paper/70')
    expect(refundCopy?.classes()).not.toContain('bg-danger/10')
    expect(confirmButton?.classes()).toContain('bg-settlement')
  })

  it('shows the C-2 warning surface and destructive action for a no-show forfeiture', () => {
    const wrapper = mountSheet(true)
    const warningCopy = wrapper
      .findAll('p')
      .find((paragraph) => paragraph.text().includes('will not be refunded'))
    const confirmButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Leave and forfeit')

    expect(warningCopy?.classes()).toEqual(
      expect.arrayContaining(['border-danger/40', 'bg-danger/10', 'text-danger']),
    )
    expect(confirmButton?.classes()).toEqual(
      expect.arrayContaining(['bg-danger', 'text-on-category']),
    )
  })

  it('emits confirm from the destructive action', async () => {
    const wrapper = mountSheet(true)
    const confirmButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Leave and forfeit')

    await confirmButton?.trigger('click')

    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })
})
