import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import AppTicket from '@/shared/ui/AppTicket.vue'

import { reportKpiFixture, reportKpiZeroFixture } from '../__fixtures__/reportPresentation'
import ReportKpiCard from '../ReportKpiCard.vue'

const LABELS = {
  totalLabel: 'Total spent',
  dailyAverageLabel: 'Daily avg',
}

describe('ReportKpiCard', () => {
  // i18n 플러그인 없이 마운트한다. props-only 계약이 실제로 상위와 끊겨 있는지 보는 것이다.
  it('renders both amounts and their labels from props alone', () => {
    const wrapper = mount(ReportKpiCard, {
      props: { data: reportKpiFixture, ...LABELS },
    })

    expect(wrapper.text()).toContain('Total spent')
    expect(wrapper.text()).toContain('₩1,284,500')
    expect(wrapper.findAll('dd')[0]?.classes()).toContain('text-data-lg')
    expect(wrapper.text()).toContain('Daily avg')
    expect(wrapper.text()).toContain('₩142,700')
  })

  it('renders a zero journey without collapsing the layout', () => {
    const wrapper = mount(ReportKpiCard, {
      props: { data: reportKpiZeroFixture, ...LABELS },
    })

    expect(wrapper.findAll('dd').map((amount) => amount.text())).toEqual(['₩0', '₩0'])
  })

  it('renders the section heading only when one is passed', () => {
    const withoutHeading = mount(ReportKpiCard, {
      props: { data: reportKpiFixture, ...LABELS },
    })
    const withHeading = mount(ReportKpiCard, {
      props: { data: reportKpiFixture, ...LABELS, heading: 'Analysis' },
    })

    expect(withoutHeading.find('h2').exists()).toBe(false)
    expect(withHeading.get('h2').text()).toBe('Analysis')
  })

  it('formats amounts in the locale it is given', () => {
    const wrapper = mount(ReportKpiCard, {
      props: { data: reportKpiFixture, ...LABELS, locale: 'ja' },
    })

    // 로케일에 따라 구분 기호가 달라져도 값은 같아야 한다.
    expect(wrapper.text()).toContain('1,284,500')
  })

  it('keeps a long formatted amount inside the amount column', () => {
    const wrapper = mount(ReportKpiCard, {
      props: {
        data: {
          totalSpent: 9_999_999_999_999,
          dailyAverage: 999_999_999,
          currency: 'KRW',
        },
        ...LABELS,
      },
    })

    const total = wrapper.findAll('dd')[0]

    expect(total?.text()).toBe('₩9,999,999,999,999')
    expect(total?.classes()).toContain('break-all')
  })

  it('aligns ticket geometry and total typography to a narrow container', async () => {
    type ResizeEntry = { contentRect: { width: number } }
    type ResizeCallback = (entries: ResizeEntry[]) => void
    class TestResizeObserver {
      static instances: TestResizeObserver[] = []

      readonly callback: ResizeCallback
      disconnected = false

      constructor(callback: ResizeCallback) {
        this.callback = callback
        TestResizeObserver.instances.push(this)
      }

      observe(): void {}

      disconnect(): void {
        this.disconnected = true
      }
    }

    vi.stubGlobal('ResizeObserver', TestResizeObserver)

    try {
      const wrapper = mount(ReportKpiCard, {
        props: { data: reportKpiFixture, ...LABELS },
      })
      const observer = TestResizeObserver.instances[0]
      const ticket = wrapper.getComponent(AppTicket)

      expect(observer).toBeDefined()
      expect(ticket.findAll('div')[1]?.attributes('style')).toContain('width: 253px')
      expect(ticket.classes()).toContain('min-h-35')
      expect(wrapper.findAll('dd')[0]?.classes()).toContain('text-data-lg')

      observer?.callback([{ contentRect: { width: 280 } }])
      await wrapper.vm.$nextTick()

      expect(ticket.findAll('div')[1]?.attributes('style')).toContain('width: 210px')
      expect(ticket.attributes('style')).toContain('at 210px')
      const perforationStyle = ticket
        .findAll('div')
        .map((element) => element.attributes('style'))
        .find((style) => style?.includes('left: 209px'))
      expect(perforationStyle).toContain('left: 209px')
      expect(wrapper.findAll('dd')[0]?.classes()).toContain('text-title')

      observer?.callback([{ contentRect: { width: 350 } }])
      await wrapper.vm.$nextTick()

      expect(ticket.findAll('div')[1]?.attributes('style')).toContain('width: 253px')
      expect(wrapper.findAll('dd')[0]?.classes()).toContain('text-data-lg')

      wrapper.unmount()
      expect(observer?.disconnected).toBe(true)
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
