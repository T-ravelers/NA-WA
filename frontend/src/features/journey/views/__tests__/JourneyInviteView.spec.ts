import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

const { fetchJourney } = vi.hoisted(() => ({ fetchJourney: vi.fn() }))

vi.mock('../../api/journeyApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/journeyApi')>()),
  fetchJourney,
}))

/* QR은 캔버스를 쓴다. jsdom에는 없고, 이 화면의 계약은 "무엇을 담느냐"다. */
const toDataURL = vi.fn()
vi.mock('qrcode', () => ({ default: { toDataURL: (...args: unknown[]) => toDataURL(...args) } }))

const JourneyInviteView = (await import('../JourneyInviteView.vue')).default

const journey = {
  tripId: 7,
  title: 'Seoul and Busan',
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  budgetAmount: 1_500_000,
  companionPreference: '2-4',
  regions: [
    { regionCode: 'BUSAN', regionName: 'Busan', displayOrder: 1 },
    { regionCode: 'SEOUL', regionName: 'Seoul', displayOrder: 0 },
  ],
}

async function mountView(path = '/journeys/7/invite') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/journeys/:tripId/invite', name: 'journey-invite', component: JourneyInviteView },
      {
        path: '/journeys/:tripId',
        name: 'journey-detail',
        component: { template: '<div>Detail</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  await router.push(path)
  await router.isReady()

  const wrapper = mount(JourneyInviteView, {
    global: { plugins: [i18n, router, [VueQueryPlugin, { queryClient }]] },
  })

  await flushPromises()
  return { router, wrapper }
}

describe('JourneyInviteView', () => {
  beforeEach(() => {
    fetchJourney.mockReset()
    fetchJourney.mockResolvedValue(journey)
    toDataURL.mockReset()
    toDataURL.mockResolvedValue('data:image/png;base64,qr')
  })

  it('shows the journey on a boarding pass', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seoul and Busan')
    expect(wrapper.text()).toContain('2026.08.10 – 2026.08.12')
  })

  /* 지역은 서버가 준 순서가 아니라 `displayOrder`가 정한 순서로 읽어야 한다. */
  it('lists the regions in their display order', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Boarding pass · Seoul · Busan')
  })

  it('keeps the boarding label readable when the journey has no region', async () => {
    fetchJourney.mockResolvedValue({ ...journey, regions: [] })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Boarding pass')
    expect(wrapper.text()).not.toContain('Boarding pass ·')
  })

  it('shows the invite code on the ticket stub', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toMatch(/TR-[2-9A-HJ-NP-Z]{4}/)
    expect(wrapper.text()).toContain('Invite code')
  })

  /*
   * 링크와 QR은 같은 곳을 가리켜야 한다. 다르면 어느 쪽으로 받았는지에 따라 다른 곳에
   * 도착하고, 그 사실은 둘을 각각 눌러 보기 전에는 드러나지 않는다.
   */
  it('puts the same journey URL behind the link button and the QR', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { ...navigator, share })

    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Copy link'))
      ?.trigger('click')
    await flushPromises()

    const sharedUrl = share.mock.calls[0]?.[0]?.url

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Show QR'))
      ?.trigger('click')
    await flushPromises()

    expect(sharedUrl).toMatch(/\/journeys\/7$/)
    expect(toDataURL).toHaveBeenCalledWith(sharedUrl, expect.anything())

    vi.unstubAllGlobals()
  })

  it('falls back to the clipboard when the device has no share sheet', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { ...navigator, share: undefined, clipboard: { writeText } })

    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Copy link'))
      ?.trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith(expect.stringMatching(/\/journeys\/7$/))

    vi.unstubAllGlobals()
  })

  /* 참여 경로가 없다는 사실을 화면이 말해야 한다. 말하지 않으면 링크가 약속처럼 읽힌다. */
  it('says that joining is not open yet', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Joining a journey is not open yet.')
  })

  it('shows the access denied state for someone else journey', async () => {
    fetchJourney.mockRejectedValue(new NormalizedApiError('JOURNEY-002', 403, 'not the owner'))

    const { wrapper } = await mountView()

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).not.toMatch(/TR-/)
  })
})
