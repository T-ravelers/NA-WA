import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import SettlementPageHeader from '../SettlementPageHeader.vue'

describe('SettlementPageHeader', () => {
  it('provides an accessible back action on settlement subpages', async () => {
    const wrapper = mount(SettlementPageHeader, { props: { title: 'SENT' } })

    await wrapper.get('[aria-label="Back"]').trigger('click')

    expect(wrapper.emitted('back')).toHaveLength(1)
    expect(wrapper.get('h1').text()).toBe('SENT')
  })
})
