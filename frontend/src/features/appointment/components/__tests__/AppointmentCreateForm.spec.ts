import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import { i18n } from '@/app/i18n'

import { appointmentExploreIntegrationKey } from '../../model/exploreIntegration'
import AppointmentCreateForm from '../AppointmentCreateForm.vue'

/**
 * 항목 상세 조회는 explore feature가 제공한다. 폼은 연동으로만 받고, 그중 위치만 쓴다
 * (운영 기간은 날짜 선택 달력을 좁히는 화면 쪽에서 쓴다).
 *
 * 마운트마다 새 ref를 세워 테스트끼리 상태를 나눠 갖지 않게 한다 — 공유 ref를
 * 바꿨다 되돌리면 되돌린 값이 원본과 어긋나 뒤 테스트가 엉뚱한 이유로 깨진다.
 */
function locationMountOptions(
  state: {
    data?: {
      placeName: string | null
      addressRoad: string | null
      startDate: string | null
      endDate: string | null
    }
    isLoading?: boolean
    isError?: boolean
  } = {},
) {
  return {
    global: {
      plugins: [i18n],
      provide: {
        [appointmentExploreIntegrationKey as symbol]: {
          useItemDetail: () => ({
            data: ref(state.data),
            isLoading: ref(state.isLoading ?? false),
            isError: ref(state.isError ?? false),
          }),
        },
      },
    },
  }
}

const mountOptions = locationMountOptions({
  data: {
    placeName: 'DDP Design Plaza',
    addressRoad: '281 Eulji-ro, Jung-gu',
    startDate: null,
    endDate: null,
  },
})

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (button === undefined) throw new Error(`Button not found: ${text}`)
  return button
}

async function fillBasics(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper
    .find('input[placeholder="e.g. Seongsu K-Beauty Tour"]')
    .setValue('Seongsu K-Beauty Tour')
  await wrapper.get('form').trigger('submit')
}

async function fillSettings(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.find('input[type="time"]').setValue('18:30')
  await wrapper.findAll('input[type="time"]')[1]?.setValue('22:00')
  await wrapper.find('input[inputmode="numeric"]').setValue('10000')
}

describe('AppointmentCreateForm', () => {
  it('offers Traditional Chinese without a Simplified Chinese option', () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      ...mountOptions,
    })

    expect(wrapper.text()).toContain('Chinese (Traditional)')
    expect(wrapper.text()).not.toContain('Chinese (Simplified)')
  })

  it('starts the language chip on the member language, not always English', async () => {
    // 목록은 회원 언어로 걸러 시작한다. 폼이 en에 머물면 일본어 회원이 그대로 만든
    // 약속이 en으로 잡혀, 돌아온 ja 목록에서 자기 약속이 보이지 않는다.
    const previous = i18n.global.locale.value
    i18n.global.locale.value = 'ja'

    try {
      const wrapper = mount(AppointmentCreateForm, {
        props: { itemId: 42, itemType: 'EVENT' },
        ...mountOptions,
      })
      await flushPromises()

      const pressed = wrapper
        .findAll('button[aria-pressed]')
        .filter((button) => button.attributes('aria-pressed') === 'true')

      expect(pressed).toHaveLength(1)
      expect(pressed[0]?.text()).toBe('日本語')
    } finally {
      i18n.global.locale.value = previous
    }
  })

  it('shows validation errors before opening confirmation', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      ...mountOptions,
    })

    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Enter an appointment name.')
    expect(wrapper.text()).not.toContain('Choose a deposit between 5,000 P and 50,000 P.')
    expect(wrapper.text()).toContain('Start with your appointment details')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('uses the activity location as the meeting place by default', () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...mountOptions,
    })

    // 대부분의 약속은 그 자리에서 그대로 만난다. 같은 주소를 손으로 옮겨 적게 하지 않는다.
    expect(wrapper.text()).toContain('Meet at DDP Design Plaza.')
    expect(wrapper.text()).toContain('281 Eulji-ro, Jung-gu')
    expect(wrapper.find('input[placeholder="Please enter the location."]').exists()).toBe(false)
  })

  it('asks for a place only when the host chooses to meet elsewhere', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...mountOptions,
    })

    await wrapper.get('#appointment-meeting-mode').setValue('CUSTOM')
    const placeInput = wrapper.find('input[placeholder="Please enter the location."]')
    expect(placeInput.exists()).toBe(true)

    await placeInput.setValue('Seongsu Beauty Lab')
    await fillBasics(wrapper)
    await fillSettings(wrapper)
    await wrapper.get('form').trigger('submit')
    await buttonByText(wrapper, 'Confirm').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      meetingPlace: 'Seongsu Beauty Lab',
    })
  })

  it('blocks submission when the activity location could not be read', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...locationMountOptions({ isError: true }),
    })

    await fillBasics(wrapper)

    expect(wrapper.text()).toContain('We could not read this activity location.')
  })

  it('says the activity location is still loading instead of blaming a failure', () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...locationMountOptions({ isLoading: true }),
    })

    // 아직 읽는 중일 뿐인데 "못 읽었다"고 말하면 사용자는 고칠 수 없는 문제로 받아들인다.
    expect(wrapper.text()).toContain('Reading the activity location')
    expect(wrapper.text()).not.toContain('We could not read this activity location.')
    expect(buttonByText(wrapper, 'Continue').attributes('disabled')).toBeDefined()
  })

  it('does not lock Continue when the item query never starts', async () => {
    // 항목 정보가 없으면 위치 조회가 꺼진 채로 있다. 이때를 "읽는 중"으로 보면 다음이
    // 영영 잠겨, 진짜 원인인 잘못된 진입 안내를 볼 방법조차 없어진다.
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, tripId: 7, visitDate: '2026-08-08' },
      ...locationMountOptions(),
    })

    expect(wrapper.text()).not.toContain('Reading the activity location')
    expect(buttonByText(wrapper, 'Continue').attributes('disabled')).toBeUndefined()

    await fillBasics(wrapper)

    expect(wrapper.text()).toContain('Open this form from an Event or Place.')
  })

  it('warns about a failed location before the host presses Continue', () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...locationMountOptions({ isError: true }),
    })

    expect(wrapper.text()).toContain('We could not read this activity location.')
    expect(buttonByText(wrapper, 'Continue').attributes('disabled')).toBeUndefined()
  })

  it('rejects a meeting place longer than the server accepts', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...mountOptions,
    })

    await wrapper.get('#appointment-meeting-mode').setValue('CUSTOM')
    await wrapper.find('input[placeholder="Please enter the location."]').setValue('a'.repeat(201))
    await fillBasics(wrapper)

    expect(wrapper.text()).toContain('Use 200 characters or fewer.')
  })

  it('moves between the basics and settings steps', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      ...mountOptions,
    })

    await fillBasics(wrapper)

    expect(wrapper.text()).toContain('Set your appointment details')
    expect(wrapper.find('input[type="time"]').exists()).toBe(true)

    await buttonByText(wrapper, 'Back').trigger('click')

    expect(wrapper.text()).toContain('Start with your appointment details')
    expect(wrapper.find('input[placeholder="e.g. Seongsu K-Beauty Tour"]').exists()).toBe(true)
  })

  it('lays the start and end times side by side with a range separator', async () => {
    // "18:30 ~ 22:00"처럼 한 줄로 읽혀야 한다. 개별 라벨은 눈에 보이지 않지만
    // 접근성 이름으로는 남는다.
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      ...mountOptions,
    })
    await fillBasics(wrapper)

    const range = wrapper.get('fieldset')
    expect(range.text()).toContain('Activity time')
    expect(range.findAll('input[type="time"]')).toHaveLength(2)
    expect(range.text()).toContain('~')
    expect(range.findAll('label.sr-only').map((label) => label.text())).toEqual([
      'Activity starts',
      'Activity ends',
    ])
  })

  it('emits a normalized request after confirming valid details', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...mountOptions,
    })

    await fillBasics(wrapper)
    await fillSettings(wrapper)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    await buttonByText(wrapper, 'Confirm').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      itemId: 42,
      itemType: 'EVENT',
      tripId: 7,
      visitDate: '2026-08-08',
      languageCode: 'en',
      appointmentName: 'Seongsu K-Beauty Tour',
      maxMembers: 4,
      depositAmount: '10000',
      meetingPlace: 'DDP Design Plaza',
      activityStartTime: '18:30:00',
      activityEndTime: '22:00:00',
    })
  })

  it('clears a basics validation error once the user starts fixing it', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT' },
      ...mountOptions,
    })

    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Enter an appointment name.')

    await wrapper
      .find('input[placeholder="e.g. Seongsu K-Beauty Tour"]')
      .setValue('Seongsu K-Beauty Tour')

    expect(wrapper.text()).not.toContain('Enter an appointment name.')
  })

  it('clears a settings validation error once the user starts fixing it', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'PLACE' },
      ...mountOptions,
    })

    await fillBasics(wrapper)
    await fillSettings(wrapper)
    await wrapper.find('input[inputmode="numeric"]').setValue('0')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Choose a deposit between 5,000 P and 50,000 P.')

    await wrapper.find('input[inputmode="numeric"]').setValue('10000')

    expect(wrapper.text()).not.toContain('Choose a deposit between 5,000 P and 50,000 P.')
  })

  it('shows a settings error as soon as the field is edited, without pressing Create', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...mountOptions,
    })

    await fillBasics(wrapper)
    await wrapper.find('input[type="time"]').setValue('18:30')
    await wrapper.findAll('input[type="time"]')[1]?.setValue('18:00')

    expect(wrapper.text()).toContain('The end time must be after the start time.')

    await wrapper.find('input[inputmode="numeric"]').setValue('100')

    expect(wrapper.text()).toContain('Choose a deposit between 5,000 P and 50,000 P.')
  })

  it('does not flag untouched settings fields until the host tries to submit', async () => {
    // 스텝에 들어오자마자 전부 빨간 화면은 틀렸다는 뜻이 아니라 아직 안 적었다는
    // 뜻일 뿐이다. 손댄 칸만 즉시 보여주고, 제출을 시도한 뒤에 전부 보여준다.
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...mountOptions,
    })

    await fillBasics(wrapper)
    expect(wrapper.text()).not.toContain('Choose a start time.')

    await wrapper.find('input[type="time"]').setValue('18:30')
    expect(wrapper.text()).not.toContain('Choose an end time.')

    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('Choose an end time.')
  })

  it('clears a schedule validation error once the user starts fixing it', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...mountOptions,
    })

    await fillBasics(wrapper)
    await fillSettings(wrapper)
    // 종료가 시작보다 늦지 않아 endAfterStart 에러가 나야 하는 값.
    await wrapper.findAll('input[type="time"]')[1]?.setValue('18:30')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('The end time must be after the start time.')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await wrapper.findAll('input[type="time"]')[1]?.setValue('22:00')

    expect(wrapper.text()).not.toContain('The end time must be after the start time.')
  })

  it('starts the deposit at 10,000 so the host can keep it without typing', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'EVENT', tripId: 7, visitDate: '2026-08-08' },
      ...mountOptions,
    })

    await fillBasics(wrapper)

    expect(wrapper.find<HTMLInputElement>('input[inputmode="numeric"]').element.value).toBe(
      '10,000',
    )

    await wrapper.find('input[type="time"]').setValue('18:30')
    await wrapper.findAll('input[type="time"]')[1]?.setValue('22:00')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).not.toContain('Choose a deposit between 5,000 P and 50,000 P.')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('10,000')
  })

  it('snapshots what the host typed and restores it, step included', async () => {
    // 충전하러 떠났다 돌아올 때 부모가 쓴다. 항목·여정·날짜는 부모가 props로 다시
    // 주므로 여기서는 적은 값과 스텝만 오간다.
    const props = { itemId: 42, itemType: 'EVENT' as const, tripId: 7, visitDate: '2026-08-08' }
    const source = mount(AppointmentCreateForm, { props, ...mountOptions })
    await fillBasics(source)
    await source.find('input[type="time"]').setValue('18:30')
    // 종료 시각은 일부러 비워 둔 채 떠난다 — 돌아온 뒤 미입력 칸이 바로 보이는지 본다.
    const saved = (source.vm as unknown as { snapshot: () => unknown }).snapshot()

    const target = mount(AppointmentCreateForm, { props, ...mountOptions })
    ;(target.vm as unknown as { restore: (saved: unknown) => void }).restore(saved)
    await flushPromises()

    expect(target.text()).toContain('Set your appointment details')
    expect(target.find<HTMLInputElement>('input[type="time"]').element.value).toBe('18:30')
    expect(target.findAll<HTMLInputElement>('input[type="time"]')[1]?.element.value).toBe('')
    expect(target.find<HTMLInputElement>('input[inputmode="numeric"]').element.value).toBe('10,000')
    // 제출까지 갔던 폼이라 돌아온 뒤에는 손대지 않은 칸의 누락도 바로 보인다.
    expect(target.text()).toContain('Choose an end time.')
  })

  it('rejects a deposit outside the configured range', async () => {
    const wrapper = mount(AppointmentCreateForm, {
      props: { itemId: 42, itemType: 'PLACE' },
      ...mountOptions,
    })

    await fillBasics(wrapper)
    await fillSettings(wrapper)
    await wrapper.find('input[inputmode="numeric"]').setValue('0')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Choose a deposit between 5,000 P and 50,000 P.')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
