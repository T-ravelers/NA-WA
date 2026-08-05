import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from '../App.vue'
import { i18n } from '../i18n'

describe('App', () => {
  it('renders the application name', () => {
    const wrapper = mount(App, {
      global: {
        plugins: [i18n],
      },
    })

    expect(wrapper.get('h1').text()).toBe('NA-WA')
    expect(wrapper.text()).toContain('Plan, travel and settle up together')
  })
})
