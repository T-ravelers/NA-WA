import { describe, expect, it } from 'vitest'

import { assertApiBaseUrlConfigured } from '../apiBaseUrl'

describe('assertApiBaseUrlConfigured', () => {
  it('값이 있으면 통과시킨다', () => {
    expect(() => assertApiBaseUrlConfigured('http://localhost:8080')).not.toThrow()
  })

  // 회귀: 값이 없으면 axios가 상대 경로로 요청하고 dev 서버의 SPA fallback이 앱 셸
  // HTML을 200으로 돌려준다. 조용히 넘어가면 원인을 찾기 어렵다.
  it('값이 없으면 기동을 멈춘다', () => {
    expect(() => assertApiBaseUrlConfigured(undefined)).toThrow(/VITE_API_BASE_URL/)
  })

  it('빈 문자열과 공백만 있는 값도 없는 것으로 본다', () => {
    expect(() => assertApiBaseUrlConfigured('')).toThrow(/VITE_API_BASE_URL/)
    expect(() => assertApiBaseUrlConfigured('   ')).toThrow(/VITE_API_BASE_URL/)
  })

  // 오류만 던지고 복구 방법을 알려주지 않으면 다음 사람이 다시 찾아야 한다.
  it('복구 방법을 오류 메시지에 담는다', () => {
    expect(() => assertApiBaseUrlConfigured(undefined)).toThrow(/\.env\.example/)
  })
})
