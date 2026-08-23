import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { computed, ref, type Ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'
import { NormalizedApiError } from '@/shared/api/apiError'

import type { MemberProfile } from '../../api/memberApi'
import {
  memberAppointmentIntegrationKey,
  type MyAppointmentItem,
} from '../../model/appointmentIntegration'
import { memberExploreIntegrationKey, type SavedExploreItem } from '../../model/exploreIntegration'

const fetchMemberProfile = vi.fn()
const updateMemberProfile = vi.fn()
const applyLocale = vi.fn()
const requestSignOut = vi.fn()

vi.mock('../../api/memberApi', () => ({
  fetchMemberProfile: () => fetchMemberProfile(),
  updateMemberProfile: (payload: unknown) => updateMemberProfile(payload),
}))

vi.mock('@/app/i18n/applyLocale', () => ({
  applyLocale: (...args: unknown[]) => applyLocale(...args),
}))

vi.mock('@/shared/api/sessionSignOut', () => ({
  requestSignOut: () => requestSignOut(),
}))

const ProfileView = (await import('../ProfileView.vue')).default

const PROFILE: MemberProfile = {
  memberId: 1,
  displayName: 'Mina',
  profileImageUrl: null,
  nationalityCode: null,
  preferredLanguage: 'en',
  preferredCurrencyCode: null,
  accountType: 'TRAVELER',
  onboardingRequired: false,
}

const SAVED_EVENT: SavedExploreItem = {
  itemId: 11,
  title: 'Seoul Lantern Festival',
  subtitle: 'Seoul · Jongno',
  thumbnailUrl: null,
}

const SAVED_PLACE: SavedExploreItem = {
  itemId: 22,
  title: 'Gwangjang Market',
  subtitle: 'Seoul',
  thumbnailUrl: null,
}

const EVENT_APPOINTMENT: MyAppointmentItem = {
  appointmentId: 71,
  appointmentName: 'Lantern night walk',
  itemType: 'EVENT',
  meetingPlace: 'Exit 3',
  activityStartAt: '2026-09-02T19:00:00',
}

const PLACE_APPOINTMENT: MyAppointmentItem = {
  appointmentId: 72,
  appointmentName: 'Market food crawl',
  itemType: 'PLACE',
  meetingPlace: null,
  activityStartAt: '2026-09-05T12:30:00',
}

interface MountOptions {
  profile?: MemberProfile
  savedByKind?: Record<'EVENT' | 'PLACE', SavedExploreItem[]>
  appointments?: MyAppointmentItem[]
  savedFails?: boolean
}

function mountView({
  profile,
  savedByKind = { EVENT: [SAVED_EVENT], PLACE: [SAVED_PLACE] },
  appointments = [EVENT_APPOINTMENT, PLACE_APPOINTMENT],
  savedFails = false,
}: MountOptions = {}) {
  // 기본 응답은 `beforeEach`가 세운다. 여기서 무조건 덮어쓰면 실패를 세워 둔 테스트가
  // 성공 응답을 받아 오류 화면을 증명하지 못한다.
  if (profile !== undefined) {
    fetchMemberProfile.mockResolvedValue(profile)
  }

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/explore/events/:eventId', component: { template: '<div />' } },
      { path: '/explore/places/:placeId', component: { template: '<div />' } },
      { path: '/appointments/:appointmentId', component: { template: '<div />' } },
    ],
  })

  const savedRefetch = vi.fn()

  const wrapper = mount(ProfileView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
      provide: {
        [memberExploreIntegrationKey as symbol]: {
          useSavedItems: (kind: Ref<'EVENT' | 'PLACE'>) => ({
            data: computed(() => (savedFails ? undefined : savedByKind[kind.value])),
            isPending: ref(false),
            isError: ref(savedFails),
            refetch: savedRefetch,
          }),
        },
        [memberAppointmentIntegrationKey as symbol]: {
          useMyAppointments: () => ({
            data: ref(appointments),
            isPending: ref(false),
            isError: ref(false),
            refetch: vi.fn(),
          }),
        },
      },
    },
  })

  return { wrapper, router, savedRefetch }
}

async function mounted(options: MountOptions = {}) {
  const mountResult = mountView(options)
  await mountResult.router.isReady()
  await flushPromises()

  return mountResult
}

/** 시트에서 n번째 언어를 고른다. 순서는 SUPPORTED_LOCALES를 따른다. */
async function chooseLanguage(wrapper: ReturnType<typeof mountView>['wrapper'], index: number) {
  await wrapper.get('[aria-label="Change screen language"]').trigger('click')
  await wrapper.findAll('[role="dialog"] [role="radio"]')[index]?.trigger('click')
  await flushPromises()
}

describe('ProfileView', () => {
  beforeEach(() => {
    // 재시도가 켜져 있으면 실패 상태가 화면에 늦게 도달해 단언이 흔들린다.
    queryClient.setDefaultOptions({ queries: { retry: false } })
    fetchMemberProfile.mockResolvedValue(PROFILE)
    updateMemberProfile.mockResolvedValue(PROFILE)
    requestSignOut.mockResolvedValue(undefined)
  })

  afterEach(() => {
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('shows the signed-in member', async () => {
    const { wrapper } = await mounted()

    expect(wrapper.text()).toContain('Mina')
  })

  it('names the nationality in the current language', async () => {
    const { wrapper } = await mounted({ profile: { ...PROFILE, nationalityCode: 'JP' } })

    expect(wrapper.text()).toContain('From Japan')
  })

  // 한 테스트 안에서 두 번 mount하면 `queryClient` 캐시가 남아 앞선 프로필이 그대로 그려진다.
  it('leaves out the nationality line without a code', async () => {
    const { wrapper } = await mounted()

    expect(wrapper.text()).not.toContain('From ')
  })

  it('leaves out the nationality line when the code is not a real region', async () => {
    const { wrapper } = await mounted({ profile: { ...PROFILE, nationalityCode: 'ZZ' } })

    expect(wrapper.text()).not.toContain('ZZ')
  })

  it('signs out from the account section', async () => {
    const { wrapper } = await mounted()

    await wrapper.get('[aria-label="Sign out"]').trigger('click')
    await flushPromises()

    expect(requestSignOut).toHaveBeenCalledOnce()
  })

  it('shows an error state with a retry when the profile cannot be loaded', async () => {
    fetchMemberProfile.mockRejectedValue(new NormalizedApiError('MEMBER-001', 404, 'not found'))

    const { wrapper } = await mounted()

    expect(wrapper.get('[role="alert"]').text()).toContain('Something went wrong')
    expect(wrapper.text()).not.toContain('Mina')
  })

  it('lists saved events first and links each one to its detail', async () => {
    const { wrapper } = await mounted()
    const list = wrapper.get('[data-testid="profile-list"]')

    expect(list.text()).toContain('Seoul Lantern Festival')
    expect(list.get('a').attributes('href')).toBe('/explore/events/11')
  })

  it('switches the saved list to places', async () => {
    const { wrapper } = await mounted()

    await wrapper.get('[data-testid="profile-kind-PLACE"]').trigger('click')

    const list = wrapper.get('[data-testid="profile-list"]')
    expect(list.text()).toContain('Gwangjang Market')
    expect(list.get('a').attributes('href')).toBe('/explore/places/22')
  })

  it('keeps one primary segment and names the kind chips independently of the tab', async () => {
    const { wrapper } = await mounted()

    expect(wrapper.findAll('[role="radiogroup"]')).toHaveLength(1)
    expect(wrapper.get('[role="group"]').attributes('aria-label')).toBe('Type')
    expect(wrapper.get('[data-testid="profile-kind-EVENT"]').attributes('aria-pressed')).toBe(
      'true',
    )

    await wrapper.get('[data-testid="segment-appointments"]').trigger('click')

    expect(wrapper.get('[role="group"]').attributes('aria-label')).toBe('Type')
    expect(wrapper.get('[data-testid="profile-kind-EVENT"]').attributes('aria-pressed')).toBe(
      'true',
    )
  })

  it('offers a retry when the saved list fails', async () => {
    const { wrapper, savedRefetch } = await mounted({ savedFails: true })

    await wrapper.get('[data-testid="profile-list"] button').trigger('click')

    expect(savedRefetch).toHaveBeenCalledOnce()
  })

  it('explains an empty saved list for the chosen kind', async () => {
    const { wrapper } = await mounted({ savedByKind: { EVENT: [], PLACE: [SAVED_PLACE] } })

    expect(wrapper.get('[data-testid="profile-list"]').text()).toContain(
      'Tap the heart on an event',
    )
  })

  it('keeps only the appointments of the chosen kind and shows when they start', async () => {
    const { wrapper } = await mounted()

    await wrapper.get('[data-testid="segment-appointments"]').trigger('click')
    const list = wrapper.get('[data-testid="profile-list"]')

    expect(list.text()).toContain('Lantern night walk')
    expect(list.text()).not.toContain('Market food crawl')
    expect(list.text()).toContain('Exit 3')
    expect(list.get('a').attributes('href')).toBe('/appointments/71')

    await wrapper.get('[data-testid="profile-kind-PLACE"]').trigger('click')
    expect(wrapper.get('[data-testid="profile-list"]').text()).toContain('Market food crawl')
  })

  it('shows the currency without a control until the picker exists', async () => {
    const { wrapper } = await mounted({
      profile: { ...PROFILE, preferredCurrencyCode: 'KRW' },
    })

    expect(wrapper.text()).toContain('KRW')
    expect(wrapper.find('[aria-label="Currency"]').exists()).toBe(false)
  })

  it('names the applied screen language', async () => {
    const { wrapper } = await mounted()

    expect(wrapper.text()).toContain('English')
  })

  it('keeps the language sheet closed until asked', async () => {
    const { wrapper } = await mounted()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('applies the chosen language and saves it to the account', async () => {
    const { wrapper } = await mounted()

    await chooseLanguage(wrapper, 1)

    expect(applyLocale).toHaveBeenCalledWith('ja', { persist: true })
    expect(updateMemberProfile).toHaveBeenCalledWith({ preferredLanguage: 'ja' })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  // 이미 쓰고 있는 언어를 다시 고르면 MEMBER-004(변경할 항목 없음)를 부르게 된다.
  it('does not call the server when the language did not change', async () => {
    const { wrapper } = await mounted()

    await chooseLanguage(wrapper, 0)

    expect(updateMemberProfile).not.toHaveBeenCalled()
    expect(applyLocale).not.toHaveBeenCalled()
  })

  it('reports a failed save without undoing the screen language', async () => {
    updateMemberProfile.mockRejectedValue(
      new NormalizedApiError('MEMBER-002', 400, 'unsupported language'),
    )

    const { wrapper } = await mounted()

    await chooseLanguage(wrapper, 1)

    const alert = wrapper.get('[role="alert"]')

    expect(alert.text()).toContain('could not save it to your account')
    expect(alert.text()).toContain('That language is not supported yet')
    // 화면 언어는 되돌리지 않는다. 사용자가 방금 고른 선택이다.
    expect(applyLocale).toHaveBeenCalledWith('ja', { persist: true })
  })

  it('falls back to the generic notice when the failure has no mapped code', async () => {
    updateMemberProfile.mockRejectedValue(new Error('boom'))

    const { wrapper } = await mounted()

    await chooseLanguage(wrapper, 1)

    const alert = wrapper.get('[role="alert"]')

    expect(alert.text()).toContain('could not save it to your account')
    expect(alert.text()).not.toContain('not supported')
  })
})
