import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppPagination from '../AppPagination.vue'

const labels = {
  label: 'Result pages',
  previousLabel: 'Previous page',
  nextLabel: 'Next page',
  pageLabel: (page: number) => `Page ${page}`,
}

function mountPagination(props: { page: number; totalPages: number; loading?: boolean }) {
  return mount(AppPagination, { props: { ...labels, ...props } })
}

describe('AppPagination', () => {
  it('shows up to five pages around the current page and marks it active', () => {
    const wrapper = mountPagination({ page: 5, totalPages: 12 })

    const pageButtons = wrapper.findAll('button[aria-label^="Page"]')
    expect(pageButtons.map((button) => button.text())).toEqual(['4', '5', '6', '7', '8'])
    expect(wrapper.get('[aria-current="page"]').text()).toBe('6')
    expect(wrapper.get('[aria-current="page"]').classes()).toContain('font-bold')
  })

  it('emits a zero-based page when another page is selected', async () => {
    const wrapper = mountPagination({ page: 0, totalPages: 6 })

    await wrapper.get('button[aria-label="Page 3"]').trigger('click')

    expect(wrapper.emitted('change')).toEqual([[2]])
  })

  it('reads its labels from the caller', () => {
    const wrapper = mountPagination({ page: 0, totalPages: 3 })

    expect(wrapper.get('nav').attributes('aria-label')).toBe('Result pages')
    expect(wrapper.find('button[aria-label="Previous page"]').exists()).toBe(true)
    expect(wrapper.find('button[aria-label="Next page"]').exists()).toBe(true)
  })
})
