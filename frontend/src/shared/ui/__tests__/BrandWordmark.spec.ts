import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BrandWordmark from '../BrandWordmark.vue'

describe('BrandWordmark', () => {
  /*
   * 도형이라 텍스트로는 읽히지 않는다. 브랜드명을 스크린 리더가 놓치지 않도록
   * 이름을 붙인 이미지로 노출한다. 장식이 아니므로 감추지 않는다.
   */
  it('announces the brand name to assistive technology', () => {
    const svg = mount(BrandWordmark).get('svg')

    expect(svg.attributes('role')).toBe('img')
    expect(svg.attributes('aria-label')).toBe('NAWA')
  })

  /*
   * 시안 실측 종횡비다. viewBox가 흔들리면 자간 -2%로 조판한 폭이 무너진다.
   */
  it('keeps the artwork aspect ratio from the design', () => {
    const svg = mount(BrandWordmark).get('svg')

    expect(svg.attributes('viewBox')).toBe('0 0 187.15 49.56')
    expect(svg.attributes('height')).toBe('52')
  })

  it('scales to the requested height', () => {
    const svg = mount(BrandWordmark, { props: { height: 24 } }).get('svg')

    expect(svg.attributes('height')).toBe('24')
    // 폭은 종횡비로 따라온다. 고정 폭을 박아 두지 않는다.
    expect(svg.attributes('width')).toBeUndefined()
  })

  /* 부모의 텍스트 색 유틸리티로 칠한다. 컴포넌트가 색을 정하지 않는다. */
  it('inherits its colour from the parent', () => {
    expect(mount(BrandWordmark).get('svg').attributes('fill')).toBe('currentColor')
  })
})
