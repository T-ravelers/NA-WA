import { beforeEach, describe, expect, it } from 'vitest'

import { clearReturnPath, consumeReturnPath, peekReturnPath, storeReturnPath } from '../returnPath'

describe('returnPath', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('restores an in-app path across the OAuth redirect', () => {
    storeReturnPath('/wallet')

    expect(consumeReturnPath()).toBe('/wallet')
  })

  it('keeps query parameters, which the backend allow list cannot carry', () => {
    storeReturnPath('/explore?keyword=seoul&sort=LATEST')

    expect(consumeReturnPath()).toBe('/explore?keyword=seoul&sort=LATEST')
  })

  it('returns the path only once', () => {
    storeReturnPath('/wallet')
    consumeReturnPath()

    expect(consumeReturnPath()).toBeNull()
  })

  it('clears a stored return path without consuming it', () => {
    storeReturnPath('/wallet')

    clearReturnPath()

    expect(peekReturnPath()).toBeNull()
  })

  it('rejects a protocol relative path that would leave the app', () => {
    storeReturnPath('//evil.example.com/steal')

    expect(consumeReturnPath()).toBeNull()
  })

  it('rejects an absolute URL', () => {
    storeReturnPath('https://evil.example.com')

    expect(consumeReturnPath()).toBeNull()
  })

  it('ignores a non string value', () => {
    storeReturnPath(['/wallet'])

    expect(consumeReturnPath()).toBeNull()
  })

  // 회귀: 로그인 실패 후 재시도해도 원래 목적지가 남아 있어야 한다.
  it('reads without consuming so a failed sign-in can be retried', () => {
    storeReturnPath('/wallet')

    expect(peekReturnPath()).toBe('/wallet')
    expect(peekReturnPath()).toBe('/wallet')
    expect(consumeReturnPath()).toBe('/wallet')
  })

  it('applies the same validation when peeking', () => {
    storeReturnPath('/wallet')
    sessionStorage.setItem('nawa.auth.returnPath', '//evil.example.com')

    expect(peekReturnPath()).toBeNull()
  })

  it('returns null from peek when nothing is stored', () => {
    expect(peekReturnPath()).toBeNull()
  })

  it('discards a stored value that is no longer valid', () => {
    sessionStorage.setItem('nawa.auth.returnPath', '//evil.example.com')

    expect(consumeReturnPath()).toBeNull()
    expect(sessionStorage.getItem('nawa.auth.returnPath')).toBeNull()
  })
})
