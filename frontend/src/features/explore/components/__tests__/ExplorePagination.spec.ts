import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import ExplorePagination from '../ExplorePagination.vue'

describe('ExplorePagination', () => {
  it('shows up to five pages around the current page and marks it active', () => {
    const wrapper = mount(ExplorePagination, {
      global: { plugins: [i18n] },
      props: { page: 5, totalPages: 12 },
    })

    const pageButtons = wrapper.findAll('button[aria-label^="Page"]')
    expect(pageButtons.map((button) => button.text())).toEqual(['4', '5', '6', '7', '8'])
    expect(wrapper.get('[aria-current="page"]').text()).toBe('6')
    expect(wrapper.get('[aria-current="page"]').classes()).toContain('font-bold')
  })

  it('emits a zero-based page when another page is selected', async () => {
    const wrapper = mount(ExplorePagination, {
      global: { plugins: [i18n] },
      props: { page: 0, totalPages: 6 },
    })

    await wrapper.get('button[aria-label="Page 3"]').trigger('click')

    expect(wrapper.emitted('change')).toEqual([[2]])
  })
})
