import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import type { SettlementCandidate } from '../../model/settlement'
import SettlementCreateView from '../SettlementCreateView.vue'

const { create, uploadReceipt, recognizeReceipt } = vi.hoisted(() => ({
  create: vi.fn(),
  uploadReceipt: vi.fn(),
  recognizeReceipt: vi.fn(),
}))
vi.mock('../../api/settlementGateway', () => ({
  settlementGateway: { create, uploadReceipt, recognizeReceipt },
}))

/** jsdom에는 미리보기 주소를 만드는 기능이 없어 대역을 둔다. */
const revokeObjectURL = vi.fn()
Object.defineProperty(URL, 'createObjectURL', { value: () => 'blob:receipt', writable: true })
Object.defineProperty(URL, 'revokeObjectURL', { value: revokeObjectURL, writable: true })

/**
 * jsdom에는 카메라가 없다. getUserMedia를 흉내 내 촬영 화면이 열리는 경우와, 아예
 * 열리지 않는 경우를 모두 확인한다.
 */
function stubCamera(stream: unknown = { getTracks: () => [] }): void {
  Object.defineProperty(navigator, 'mediaDevices', {
    value:
      stream === null
        ? undefined
        : { getUserMedia: vi.fn().mockResolvedValue(stream as MediaStream) },
    configurable: true,
  })
  Object.defineProperty(HTMLMediaElement.prototype, 'play', {
    value: vi.fn().mockResolvedValue(undefined),
    configurable: true,
  })
}

async function openCamera(wrapper: ReturnType<typeof mountCreate>): Promise<void> {
  await wrapper.get('[data-action="add-receipt"]').trigger('click')
  await wrapper.get('[data-action="receipt-source-camera"]').trigger('click')
  await flushPromises()
}

async function fillItem(
  wrapper: ReturnType<typeof mountCreate>,
  index: number,
  values: { name: string; unitPrice: string; quantity: string },
): Promise<void> {
  await wrapper.get(`[data-item-name="${index}"]`).setValue(values.name)
  await wrapper.get(`[data-item-unit-price="${index}"]`).setValue(values.unitPrice)
  await wrapper.get(`[data-item-quantity="${index}"]`).setValue(values.quantity)
}

function itemValue(wrapper: ReturnType<typeof mountCreate>, selector: string): string {
  return (wrapper.get(selector).element as HTMLInputElement).value
}

async function allocate(
  wrapper: ReturnType<typeof mountCreate>,
  index: number,
  participantId: string,
  quantity: string,
): Promise<void> {
  await wrapper.get(`[data-allocation-quantity="${index}:${participantId}"]`).setValue(quantity)
}

function pngFile(name = 'receipt.png', type = 'image/png', size = 1024): File {
  const file = new File([new Uint8Array(1)], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

/** 영수증 버튼 → 출처 선택 시트 → 저장소 선택까지, 사용자가 밟는 순서 그대로 간다. */
async function pickReceipt(
  wrapper: ReturnType<typeof mountCreate>,
  file: File = pngFile(),
): Promise<void> {
  await wrapper.get('[data-action="add-receipt"]').trigger('click')
  await wrapper.get('[data-action="receipt-source-library"]').trigger('click')

  const input = wrapper.get('[data-testid="receipt-library-input"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

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

function mountCreate(candidates: SettlementCandidate[] = [candidate()]) {
  return mount(SettlementCreateView, {
    props: { candidates },
    global: { plugins: [i18n] },
  })
}

async function drillDownToTransaction(wrapper: ReturnType<typeof mountCreate>) {
  await wrapper.get('[data-journey-key="Seoul"]').trigger('click')
  await wrapper.get('[data-appointment-id="9"]').trigger('click')
  await wrapper.get('[data-payment-id="7"]').trigger('click')
  await wrapper.get('[data-action="next"]').trigger('click')
}

describe('SettlementCreateView', () => {
  beforeEach(() => {
    create.mockReset().mockResolvedValue({ id: '42' })
    uploadReceipt.mockReset().mockResolvedValue({ receiptId: '31' })
    recognizeReceipt.mockReset().mockResolvedValue({
      items: [{ name: 'Wine', unitPrice: '25.00', quantity: '1' }],
      recognizedTotal: '25.00',
    })
  })

  it('narrows a journey to an appointment before offering its payments', async () => {
    const wrapper = mountCreate([
      candidate(),
      candidate({ transferId: '8', appointmentId: '10', gatheringName: 'Cafe' }),
      candidate({ transferId: '9', journeyName: 'Busan', appointmentId: '11' }),
    ])
    expect(wrapper.findAll('[data-journey-key]')).toHaveLength(2)
    expect(wrapper.find('[data-payment-id]').exists()).toBe(false)

    await wrapper.get('[data-journey-key="Seoul"]').trigger('click')
    expect(wrapper.findAll('[data-appointment-id]')).toHaveLength(2)
    expect(wrapper.find('[data-payment-id]').exists()).toBe(false)

    await wrapper.get('[data-appointment-id="9"]').trigger('click')
    expect(wrapper.findAll('[data-payment-id]')).toHaveLength(1)
  })

  it('counts payments in singular and plural', () => {
    const wrapper = mountCreate([
      candidate(),
      candidate({ transferId: '8', appointmentId: '10', gatheringName: 'Cafe' }),
      candidate({ transferId: '9', journeyName: 'Busan', appointmentId: '11' }),
    ])

    expect(wrapper.get('[data-journey-key="Seoul"]').text()).toContain('2 payments')
    expect(wrapper.get('[data-journey-key="Busan"]').text()).toContain('1 payment')
    expect(wrapper.get('[data-journey-key="Busan"]').text()).not.toContain('1 payments')
  })

  it('keeps the first step from continuing until a payment is chosen', async () => {
    const wrapper = mountCreate()
    expect(wrapper.get('[data-action="next"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-journey-key="Seoul"]').trigger('click')
    await wrapper.get('[data-appointment-id="9"]').trigger('click')
    expect(wrapper.get('[data-action="next"]').attributes('disabled')).toBeDefined()

    await wrapper.get('[data-payment-id="7"]').trigger('click')
    expect(wrapper.get('[data-action="next"]').attributes('disabled')).toBeUndefined()
  })

  it('blocks the review step until at least two participants are chosen', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Choose at least two participants to continue')

    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Request overview')
  })

  it('always includes the candidate payer appointment member when creating an even split', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')

    expect(wrapper.get('[data-action="create"]').text()).toBe('Send request')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    expect(create).toHaveBeenCalledWith(
      '9',
      expect.any(String),
      expect.objectContaining({
        sourceTransferId: '7',
        type: 'EQUAL',
        participantAppointmentMemberIds: ['12', '19'],
      }),
    )
    expect(wrapper.emitted('complete')).toEqual([['42']])
  })

  it('blocks itemized creation until each item quantity is allocated', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await wrapper.get('[data-participant-id="19"]').trigger('click')

    await wrapper.get('[data-item-name="0"]').setValue('Pasta')
    await wrapper.get('[data-item-unit-price="0"]').setValue('12.50')
    await wrapper.get('[data-item-quantity="0"]').setValue('2')
    await wrapper.get('[data-allocation-quantity="0:12"]').setValue('1')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Allocate every item quantity before continuing')

    await wrapper.get('[data-allocation-quantity="0:19"]').setValue('1')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Request overview')
  })

  it('holds the sending screen after a success so the request cannot be sent twice', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    // 부모가 다음 화면으로 넘기기 전까지 검토 화면이 다시 보이면 안 된다. 멱등키는 이미
    // 지워져 있어 두 번째 요청은 새 키로 나간다.
    expect(wrapper.find('[data-action="create"]').exists()).toBe(false)
    expect(wrapper.emitted('submittingChange')).toEqual([[true]])
    expect(create).toHaveBeenCalledTimes(1)
  })

  it('returns to the review step when the request fails', async () => {
    create.mockRejectedValueOnce(new NormalizedApiError('SETTLEMENT-005', 400, 'invalid'))
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-action="create"]').exists()).toBe(true)
    expect(wrapper.emitted('submittingChange')).toEqual([[true], [false]])
    expect(wrapper.emitted('complete')).toBeUndefined()
  })

  it('drops the allocations of a participant who is removed', async () => {
    const wrapper = mountCreate([
      candidate({
        participants: [
          { id: '12', name: 'Alex', initials: 'AL' },
          { id: '19', name: 'Mina', initials: 'MI' },
          { id: '27', name: 'Sora', initials: 'SO' },
        ],
      }),
    ])
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-participant-id="27"]').trigger('click')

    await wrapper.get('[data-item-name="0"]').setValue('Pasta')
    await wrapper.get('[data-item-unit-price="0"]').setValue('12.50')
    await wrapper.get('[data-item-quantity="0"]').setValue('3')
    await wrapper.get('[data-allocation-quantity="0:12"]').setValue('1')
    await wrapper.get('[data-allocation-quantity="0:19"]').setValue('1')
    await wrapper.get('[data-allocation-quantity="0:27"]').setValue('1')

    await wrapper.get('[data-participant-id="27"]').trigger('click')

    // 다시 선택하면 이전 값이 아니라 빈 칸으로 시작한다. 값이 지워졌다는 증거다.
    await wrapper.get('[data-participant-id="27"]').trigger('click')
    expect(
      (wrapper.get('[data-allocation-quantity="0:27"]').element as HTMLInputElement).value,
    ).toBe('')

    // 해제한 사람의 배분값이 남으면, 보이는 칸이 모두 맞아도 검증이 숨은 1을 계속 더해
    // 어떤 편집으로도 다음 단계로 넘어갈 수 없다.
    await wrapper.get('[data-participant-id="27"]').trigger('click')
    await wrapper.get('[data-item-quantity="0"]').setValue('2')
    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.text()).toContain('Request overview')
  })

  it('returns to the first step when the chosen payment disappears from a refetch', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')

    await wrapper.setProps({ candidates: [] })
    await flushPromises()

    expect(wrapper.text()).toContain('no longer available')
    expect(wrapper.text()).toContain('No payments available')
    const steps = wrapper.emitted('update:step') ?? []
    expect(steps[steps.length - 1]).toEqual([1])
  })

  it('keeps the wizard on its step when a refetch still holds the chosen payment', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')

    await wrapper.setProps({ candidates: [candidate(), candidate({ transferId: '8' })] })
    await flushPromises()

    expect(wrapper.text()).not.toContain('no longer available')
    expect(wrapper.get('[data-participant-id="19"]').attributes('aria-pressed')).toBe('true')
  })

  it('leaves the wizard alone when candidates change after a successful submit', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    // 성공 직후 부모의 무효화가 정산된 결제를 뺀 목록을 내려보낸다. 이때 1단계로 되돌리면
    // 부모가 다음 화면으로 넘기기 전까지 살아 있는 성공 화면 뒤에서 상태가 뒤집힌다.
    await wrapper.setProps({ candidates: [] })
    await flushPromises()

    const steps = wrapper.emitted('update:step') ?? []
    expect(steps[steps.length - 1]).toEqual([3])
  })

  it('clears the gone-payment notice when the user changes the journey', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')

    await wrapper.setProps({ candidates: [] })
    await flushPromises()
    await wrapper.setProps({ candidates: [candidate({ transferId: '8' })] })
    await flushPromises()
    expect(wrapper.text()).toContain('no longer available')

    // 여정을 갈아타는 순간은 이미 다른 결제를 고르는 중이라 안내가 소임을 다한 시점이다.
    await wrapper.get('[data-action="change-journey"]').trigger('click')
    expect(wrapper.text()).not.toContain('no longer available')
  })

  it('uploads the chosen receipt and sends its id with the split', async () => {
    uploadReceipt.mockResolvedValue({ receiptId: '31' })
    create.mockResolvedValue({ id: '77' })
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    await pickReceipt(wrapper)

    expect(uploadReceipt).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    expect(create.mock.calls[0]?.[2]).toMatchObject({ receiptId: '31' })
  })

  it('omits receiptId when no receipt was attached', async () => {
    create.mockResolvedValue({ id: '77' })
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    expect(create.mock.calls[0]?.[2]).not.toHaveProperty('receiptId')
    expect(uploadReceipt).not.toHaveBeenCalled()
  })

  it('rejects an oversized photo before spending the upload', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    await pickReceipt(wrapper, pngFile('big.png', 'image/png', 9 * 1024 * 1024))

    expect(uploadReceipt).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('over 8 MB')
  })

  it('offers both the camera and the photo library', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    await wrapper.get('[data-action="add-receipt"]').trigger('click')

    // 영수증은 즉석에서 찍기도 하고 이미 찍어 둔 것을 고르기도 한다. 둘 다 열려 있어야 한다.
    expect(wrapper.find('[data-action="receipt-source-camera"]').exists()).toBe(true)
    expect(wrapper.find('[data-action="receipt-source-library"]').exists()).toBe(true)
  })

  it('opens an in-app camera instead of a file dialog', async () => {
    // 노트북에서는 파일 입력의 capture 속성이 무시돼 파일 창만 열린다. 촬영은 앱 안에서 한다.
    stubCamera()
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    await openCamera(wrapper)

    expect(wrapper.find('[data-action="receipt-camera-shoot"]').exists()).toBe(true)
  })

  it('offers the photo library when the camera cannot open', async () => {
    stubCamera(null)
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    await openCamera(wrapper)

    expect(wrapper.find('[data-action="receipt-camera-shoot"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('cannot open the camera')
    // 막혀 있어도 빠져나갈 길은 남아 있어야 한다.
    expect(wrapper.find('[data-action="receipt-camera-library"]').exists()).toBe(true)
  })

  it('rejects a format the server does not accept', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    await pickReceipt(wrapper, pngFile('photo.heic', 'image/heic'))

    expect(uploadReceipt).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('JPEG, PNG, or WebP')
  })

  it('points at the item that blocks the step, and only after Continue', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')

    await wrapper.get('[data-action="add-item"]').trigger('click')
    await fillItem(wrapper, 0, { name: 'Pasta', unitPrice: '10', quantity: '1' })
    await allocate(wrapper, 0, '12', '1')
    // 두 번째 품목은 배분 합이 수량과 어긋난다.
    await fillItem(wrapper, 1, { name: 'Wine', unitPrice: '20', quantity: '2' })
    await allocate(wrapper, 1, '12', '1')

    // 아직 누르기 전에는 아무 표시도 하지 않는다.
    expect(wrapper.find('[data-item-invalid="true"]').exists()).toBe(false)

    await wrapper.get('[data-action="next"]').trigger('click')

    // 합이 어긋난 두 번째 품목만 표시돼야 한다. 멀쩡한 첫 품목까지 빨개지면 소용이 없다.
    const flagged = wrapper.findAll('[data-item-invalid="true"]')
    expect(flagged).toHaveLength(1)
    expect(flagged[0]?.find('[data-item-name="1"]').exists()).toBe(true)
  })

  it('clears the item marks once the step goes through', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await fillItem(wrapper, 0, { name: 'Pasta', unitPrice: '25', quantity: '2' })
    await allocate(wrapper, 0, '12', '1')

    await wrapper.get('[data-action="next"]').trigger('click')
    expect(wrapper.find('[data-item-invalid="true"]').exists()).toBe(true)

    await allocate(wrapper, 0, '19', '1')
    await wrapper.get('[data-action="next"]').trigger('click')

    expect(wrapper.find('[data-item-invalid="true"]').exists()).toBe(false)
  })

  it('blocks the step when item totals do not match the payment', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')

    // 원거래는 25.00인데 품목 합계가 30.00이다. 서버가 거절할 요청이라 여기서 막는다.
    await fillItem(wrapper, 0, { name: 'Wine', unitPrice: '30.00', quantity: '1' })
    await allocate(wrapper, 0, '12', '1')

    expect(wrapper.get('[data-testid="items-total"]').text()).toContain('30')

    await wrapper.get('[data-action="next"]').trigger('click')

    // 품목 자체는 흠이 없으니 배분 안내가 아니라 합계 안내가 떠야 고칠 곳을 안다.
    expect(wrapper.text()).toContain('must match the payment')
    expect(wrapper.find('[data-action="create"]').exists()).toBe(false)
  })

  it('lets the step through once the totals line up', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await fillItem(wrapper, 0, { name: 'Dinner', unitPrice: '12.50', quantity: '2' })
    await allocate(wrapper, 0, '12', '1')
    await allocate(wrapper, 0, '19', '1')

    await wrapper.get('[data-action="next"]').trigger('click')

    expect(wrapper.find('[data-action="create"]').exists()).toBe(true)
  })

  it('shows what each person owes and what is actually requested', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await fillItem(wrapper, 0, { name: 'Dinner', unitPrice: '12.50', quantity: '2' })
    await allocate(wrapper, 0, '12', '1')
    await allocate(wrapper, 0, '19', '1')
    await wrapper.get('[data-action="next"]').trigger('click')

    expect(wrapper.get('[data-share-for="12"]').text()).toContain('12.5')
    expect(wrapper.get('[data-share-for="19"]').text()).toContain('12.5')

    // 원결제자(12)는 자기 자신에게 청구하지 않는다. 25 중 12.5만 요청한다.
    expect(wrapper.get('[data-testid="request-total"]').text()).toContain('12.5')
    expect(wrapper.get('[data-action="create"]').text()).toContain('12.5')
  })

  it('does not invent per-person amounts for an even split', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')

    // 나머지를 누가 더 낼지는 통화 단위에 달려 있어 화면이 알 수 없다. 규칙만 알린다.
    expect(wrapper.find('[data-testid="request-total"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Split evenly across 2 people')
  })

  it('drops an upload that lands after the payment was changed', async () => {
    let finishUpload: (value: { receiptId: string }) => void = () => {}
    uploadReceipt.mockReturnValueOnce(
      new Promise<{ receiptId: string }>((resolve) => {
        finishUpload = resolve
      }),
    )
    const wrapper = mountCreate([
      candidate(),
      candidate({ transferId: '8', gatheringName: 'Cafe', amount: '10.00' }),
    ])
    await drillDownToTransaction(wrapper)
    await pickReceipt(wrapper)

    // 아직 올라가는 중에 다른 결제로 옮긴다.
    ;(wrapper.vm as unknown as { back: () => void }).back()
    await flushPromises()
    await wrapper.get('[data-payment-id="8"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')

    // 버린 사진의 응답이 뒤늦게 도착한다.
    finishUpload({ receiptId: '31' })
    await flushPromises()

    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    // 되살아나면 앞 결제의 영수증이 남의 정산에 붙고, 붙은 뒤에는 바꿀 수 없다.
    expect(create.mock.calls[0]?.[2]).not.toHaveProperty('receiptId')
  })

  it('waits for the upload before letting the request through', async () => {
    let finishUpload: (value: { receiptId: string }) => void = () => {}
    uploadReceipt.mockReturnValueOnce(
      new Promise<{ receiptId: string }>((resolve) => {
        finishUpload = resolve
      }),
    )
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await pickReceipt(wrapper)

    await wrapper.get('[data-action="next"]').trigger('click')

    // 그냥 넘기면 영수증 번호가 아직 없어, 오류도 없이 사진만 빠진 정산이 만들어진다.
    expect(wrapper.find('[data-action="create"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Wait for the receipt')

    finishUpload({ receiptId: '31' })
    await flushPromises()

    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    expect(create.mock.calls[0]?.[2]).toMatchObject({ receiptId: '31' })
  })

  it('drops the receipt when another payment is chosen', async () => {
    const wrapper = mountCreate([
      candidate(),
      candidate({ transferId: '8', gatheringName: 'Cafe', amount: '10.00' }),
    ])
    await drillDownToTransaction(wrapper)
    await pickReceipt(wrapper)
    expect(uploadReceipt).toHaveBeenCalledTimes(1)

    // 1단계로 돌아가 다른 결제를 고른다.
    ;(wrapper.vm as unknown as { back: () => void }).back()
    await flushPromises()
    await wrapper.get('[data-payment-id="8"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')
    await wrapper.get('[data-action="create"]').trigger('click')
    await flushPromises()

    // 앞 결제의 영수증이 따라오면, 한 번 연결된 뒤에는 바꿀 수 없어 되돌릴 방법이 없다.
    expect(create.mock.calls[0]?.[2]).not.toHaveProperty('receiptId')
  })

  it('shows computed amounts with the same decimals as the payment', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await fillItem(wrapper, 0, { name: 'Dinner', unitPrice: '12.50', quantity: '2' })
    await allocate(wrapper, 0, '12', '1')
    await allocate(wrapper, 0, '19', '1')

    // 25.00과 25가 나란히 놓이면 같은 금액인지 눈으로 알아볼 수 없다.
    expect(wrapper.get('[data-testid="items-total"]').text()).toContain('25.00 P / 25.00 P')

    await wrapper.get('[data-action="next"]').trigger('click')

    expect(wrapper.get('[data-share-for="12"]').text()).toContain('12.50 P')
    expect(wrapper.get('[data-testid="request-total"]').text()).toContain('12.50 P')
    expect(wrapper.get('[data-action="create"]').text()).toBe('Request 12.50 P')
  })

  it('does not offer a receipt to open when none was attached', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-action="next"]').trigger('click')

    // 눌러도 아무 일이 없는 버튼은 고장으로 보인다. 없다는 것을 아는 자리라 눌리지 않게 둔다.
    const box = wrapper.get('[data-action="add-receipt"]')
    expect(box.attributes('disabled')).toBeDefined()
    expect(box.attributes('aria-label')).toBe('No receipt attached')
  })

  it('lays out the first item as soon as the itemized split is chosen', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)

    // 균등 분할에는 품목이 없다.
    expect(wrapper.find('[data-item-name="0"]').exists()).toBe(false)

    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')

    // 품목이 0개인 품목별 정산은 어차피 통과하지 못한다. 빈 자리부터 보여줄 이유가 없다.
    expect(wrapper.find('[data-item-name="0"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-item-invalid]')).toHaveLength(0)
  })

  it('keeps what was typed when the split method is toggled back', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await fillItem(wrapper, 0, { name: 'Pasta', unitPrice: '25', quantity: '1' })

    await wrapper.get('[data-type="EQUAL"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')

    // 자동으로 까는 것은 비어 있을 때뿐이다. 적어 둔 것을 밀어내면 안 된다.
    expect((wrapper.get('[data-item-name="0"]').element as HTMLInputElement).value).toBe('Pasta')
    expect(wrapper.findAll('[data-item-name]')).toHaveLength(1)
  })

  it('removes the item card that the x button belongs to', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await wrapper.get('[data-action="add-item"]').trigger('click')
    await fillItem(wrapper, 0, { name: 'Pasta', unitPrice: '10', quantity: '1' })
    await fillItem(wrapper, 1, { name: 'Wine', unitPrice: '15', quantity: '1' })

    await wrapper.get('[data-remove-item="0"]').trigger('click')

    // 지운 자리로 뒷 품목이 당겨진다. 잘못 눌러 남은 쪽이 사라지면 알아채기 어렵다.
    expect(wrapper.findAll('[data-item-name]')).toHaveLength(1)
    expect((wrapper.get('[data-item-name="0"]').element as HTMLInputElement).value).toBe('Wine')
  })

  it('hides the remove button on the last item card', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')

    // 마지막 한 장까지 지우면 어떤 편집으로도 빠져나올 수 없는 자리가 남는다.
    expect(wrapper.find('[data-remove-item]').exists()).toBe(false)

    await wrapper.get('[data-action="add-item"]').trigger('click')
    expect(wrapper.findAll('[data-remove-item]')).toHaveLength(2)

    await wrapper.get('[data-remove-item="1"]').trigger('click')
    expect(wrapper.find('[data-remove-item]').exists()).toBe(false)
  })

  /** 사진이 없으면 읽을 것이 없다. 눌러도 아무 일이 없는 버튼을 두면 고장으로 보인다. */
  it('offers the receipt reader only once a photo is attached', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')

    expect(wrapper.find('[data-action="load-items"]').exists()).toBe(false)

    await pickReceipt(wrapper)

    expect(wrapper.find('[data-action="load-items"]').exists()).toBe(true)
  })

  it('fills the item cards from the receipt and leaves the sharing to the user', async () => {
    recognizeReceipt.mockResolvedValue({
      items: [
        { name: 'Pasta', unitPrice: '10.00', quantity: '1' },
        { name: 'Wine', unitPrice: '15.00', quantity: '1' },
      ],
      recognizedTotal: '25.00',
    })
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await pickReceipt(wrapper)

    await wrapper.get('[data-action="load-items"]').trigger('click')
    await flushPromises()

    expect(itemValue(wrapper, '[data-item-name="0"]')).toBe('Pasta')
    expect(itemValue(wrapper, '[data-item-unit-price="1"]')).toBe('15.00')
    // 누가 무엇을 먹었는지는 인식이 알려주지 않는다. 비어 있어야 사용자가 정한다.
    expect(itemValue(wrapper, '[data-allocation-quantity="0:12"]')).toBe('')
    // 배분이 비었다고 방금 채운 값까지 틀린 것처럼 보이면 안 된다.
    expect(wrapper.find('[data-item-invalid="true"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="allocate-hint"]').text()).toContain('share each quantity')
  })

  /*
   * 인식은 부를 때마다 요금이 나간다. 덮어쓰지 않기로 할 요청을 미리 보낼 이유가 없어서
   * 읽기 전에 묻는다.
   */
  it('asks before replacing entered items and does not call the reader when refused', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await fillItem(wrapper, 0, { name: 'Pasta', unitPrice: '25.00', quantity: '1' })
    await pickReceipt(wrapper)

    await wrapper.get('[data-action="load-items"]').trigger('click')
    await wrapper.get('[data-action="overwrite-items-cancel"]').trigger('click')
    await flushPromises()

    expect(recognizeReceipt).not.toHaveBeenCalled()
    expect(itemValue(wrapper, '[data-item-name="0"]')).toBe('Pasta')

    await wrapper.get('[data-action="load-items"]').trigger('click')
    await wrapper.get('[data-action="overwrite-items-confirm"]').trigger('click')
    await flushPromises()

    expect(recognizeReceipt).toHaveBeenCalledWith('31')
    expect(itemValue(wrapper, '[data-item-name="0"]')).toBe('Wine')
  })

  /*
   * 영수증은 정산 방식과 무관하게 붙어 있다. 1/N으로 두고 사진만 올린 뒤 품목별로 마음을
   * 바꿔도 그 사진을 그대로 읽을 수 있어야 한다.
   */
  it('reads a receipt that was attached while the split was still even', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await pickReceipt(wrapper)

    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await wrapper.get('[data-action="load-items"]').trigger('click')
    await flushPromises()

    expect(recognizeReceipt).toHaveBeenCalledWith('31')
    expect(itemValue(wrapper, '[data-item-name="0"]')).toBe('Wine')
  })

  /** 넷 다 "다시 시도"라고 말하면 사용자는 다시 찍을지 직접 적을지 알 수 없다. */
  it.each([
    ['SETTLEMENT-022', 'WebP'],
    ['SETTLEMENT-023', 'could not find any items'],
    ['SETTLEMENT-024', 'took too long'],
    ['SETTLEMENT-025', 'unavailable right now'],
  ])('explains %s in its own words', async (code, phrase) => {
    recognizeReceipt.mockRejectedValue(new NormalizedApiError(code, 422, 'server message'))
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await pickReceipt(wrapper)

    await wrapper.get('[data-action="load-items"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="ocr-error"]').text()).toContain(phrase)
  })

  /*
   * 할인이나 봉사료가 붙거나 여러 명이 나눠 결제하면 영수증 합계와 결제 금액은 정상적으로도
   * 달라진다. 인식 값 자체가 틀렸을 수도 있어서, 알리기만 하고 막지 않는다.
   */
  it('warns about a receipt total that differs from the payment without blocking', async () => {
    recognizeReceipt.mockResolvedValue({
      items: [{ name: 'Dinner', unitPrice: '25.00', quantity: '1' }],
      recognizedTotal: '30.00',
    })
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await pickReceipt(wrapper)
    await wrapper.get('[data-action="load-items"]').trigger('click')
    await flushPromises()
    await allocate(wrapper, 0, '12', '1')

    expect(wrapper.get('[data-testid="receipt-total"]').text()).toContain('30.00 P')
    expect(wrapper.find('[data-testid="receipt-total-mismatch"]').exists()).toBe(true)

    await wrapper.get('[data-action="next"]').trigger('click')

    expect(wrapper.find('[data-action="create"]').exists()).toBe(true)
  })

  /** 사진을 바꿨는데 앞 영수증의 합계가 남아 있으면 무엇을 견주는 중인지 알 수 없다. */
  it('drops the previous reading when another photo is chosen', async () => {
    const wrapper = mountCreate()
    await drillDownToTransaction(wrapper)
    await wrapper.get('[data-participant-id="19"]').trigger('click')
    await wrapper.get('[data-type="ITEMIZED"]').trigger('click')
    await pickReceipt(wrapper)
    await wrapper.get('[data-action="load-items"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="receipt-total"]').exists()).toBe(true)

    await pickReceipt(wrapper, pngFile('another.png'))

    expect(wrapper.find('[data-testid="receipt-total"]').exists()).toBe(false)
  })
})
