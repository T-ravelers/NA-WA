import { describe, expect, it } from 'vitest'

import { isSupportedLocale } from '@/shared/i18n/locales'

import type { MessageTree } from '../messages'

/**
 * 번역 파일의 구조 검증.
 *
 * 부분 번역은 허용한다 — 없는 key는 `en`으로 폴백하는 것이 설계다. 그래서 key 개수가 같은지는
 * 보지 않는다. 대신 **있는 번역이 깨져 있지 않은지**를 본다.
 *
 * - `en`에 없는 key가 로케일 파일에 있으면 오타이거나 지워진 key의 잔재다. 화면에 절대 나오지 않는다.
 * - `{placeholder}`가 다르면 런타임에 값 대신 빈칸이 나온다.
 * - 복수형 `|` 분절 수가 다르면 `count`에 따라 엉뚱한 분절이 뽑힌다.
 *
 * 수집 경로는 `messages.ts`와 같다.
 */
const messageModules = import.meta.glob<{ default: MessageTree }>(
  ['../../../shared/i18n/*.ts', '../../../features/*/i18n/*.ts'],
  { eager: true },
)

type FlatMessages = Map<string, string>

function flatten(tree: MessageTree, prefix = '', into: FlatMessages = new Map()): FlatMessages {
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix === '' ? key : `${prefix}.${key}`

    if (typeof value === 'string') {
      into.set(path, value)
    } else {
      flatten(value, path, into)
    }
  }

  return into
}

function placeholders(message: string): string[] {
  return [...message.matchAll(/\{[^}]+\}/g)].map((match) => match[0]).sort()
}

function pluralForms(message: string): number {
  return message.split('|').length
}

/** `../../../features/wallet/i18n/ja.ts` → `features/wallet` · `ja` */
function describeModule(path: string): { group: string; locale: string } {
  const segments = path.split('/')
  const locale = segments[segments.length - 1]?.replace(/\.ts$/, '') ?? ''
  const group = segments.slice(3, -1).join('/')

  return { group, locale }
}

const groups = new Map<string, Map<string, FlatMessages>>()

for (const [path, module] of Object.entries(messageModules)) {
  const { group, locale } = describeModule(path)

  if (!isSupportedLocale(locale)) {
    continue
  }

  const locales = groups.get(group) ?? new Map<string, FlatMessages>()

  locales.set(locale, flatten(module.default))
  groups.set(group, locales)
}

describe('translation files follow en', () => {
  for (const [group, locales] of groups) {
    const source = locales.get('en')

    it(`${group} has an en source`, () => {
      expect(source).toBeDefined()
    })

    if (source === undefined) {
      continue
    }

    for (const [locale, messages] of locales) {
      if (locale === 'en') {
        continue
      }

      it(`${group}/${locale} has no key that en lacks`, () => {
        const orphans = [...messages.keys()].filter((key) => !source.has(key))

        expect(orphans).toEqual([])
      })

      it(`${group}/${locale} keeps placeholders and plural forms`, () => {
        const problems: string[] = []

        for (const [key, message] of messages) {
          const original = source.get(key)

          if (original === undefined) {
            continue
          }

          if (message.trim() === '') {
            problems.push(`${key}: empty`)
          }

          if (placeholders(message).join(',') !== placeholders(original).join(',')) {
            problems.push(
              `${key}: placeholders ${placeholders(message).join(' ')} ≠ ${placeholders(original).join(' ')}`,
            )
          }

          if (pluralForms(message) !== pluralForms(original)) {
            problems.push(`${key}: ${pluralForms(message)} plural forms ≠ ${pluralForms(original)}`)
          }
        }

        expect(problems).toEqual([])
      })
    }
  }
})
