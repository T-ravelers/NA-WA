import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import { i18n } from '@/app/i18n'

import { appointmentExploreIntegrationKey } from '../../model/exploreIntegration'
import AppointmentCreateForm from '../AppointmentCreateForm.vue'

/**
 * 항목 위치 조회는 explore feature가 제공한다. 폼은 연동으로만 받는다.
 *
 * 마운트마다 새 ref를 세워 테스트끼리 상태를 나눠 갖지 않게 한다 — 공유 ref를
 * 바꿨다 되돌리면 되돌린 값이 원본과 어긋나 뒤 테스트가 엉뚱한 이유로 깨진다.
 */
function locationMountOptions(
  state: {
    data?: { placeName: string | null; addressRoad: string | null }
    isLoading?: boolean
    isError?: boolean
  } = {},
) {
  return {
    global: {
      plugins: [i18n],
      provide: {
        [appointmentExploreIntegrationKey as symbol]: {
          useItemLocation: () => ({
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
  data: { placeName: 'DDP Design Plaza', addressRoad: '281 Eulji-ro, Jung-gu' },
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
