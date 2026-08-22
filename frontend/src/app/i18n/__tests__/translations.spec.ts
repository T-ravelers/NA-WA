import { describe, expect, it } from 'vitest'

import { SUPPORTED_LOCALES, isSupportedLocale } from '@/shared/i18n/locales'

import type { MessageTree } from '../messages'

/**
 * 번역 파일의 구조 검증.
 *
 * - `en`의 key가 로케일 파일에 없으면 그 문구만 화면에 영어로 남는다. `UNTRANSLATED`에 적은
 *   key만 예외로 둔다.
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

/**
 * 의도적으로 번역하지 않는 key. `<group>/<locale>` → key 배열이며 `group`은 `shared/i18n`
 * 또는 `features/<domain>/i18n`이다 — 아래 테스트 이름에 그대로 찍히는 값이다.
 *
 * 여기 적지 않은 key가 로케일 파일에서 빠지면 테스트가 실패한다. 비-`en`이 스텁이던 동안은
 * 부분 번역을 허용하는 것이 설계였지만, #354가 세 로케일을 전량 채우면서 전제가 바뀌었다.
 * 이제 `en`에 key를 더하면서 번역을 빠뜨린 PR은 아무 경고 없이 그 화면에 영어를 남긴다.
 * 폴백은 그대로 살아 있으므로 화면이 깨지지는 않는다 — 이 검사는 빠뜨림을 드러내기 위한 것이다.
 *
 * 번역하지 않기로 정한 key를 넣을 때는 왜 그런지 한 줄로 적는다.
 */
const UNTRANSLATED: Record<string, readonly string[]> = {}

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

    it(`${group} has a file for every supported locale`, () => {
      const absent = SUPPORTED_LOCALES.filter((locale) => !locales.has(locale))

      expect(absent).toEqual([])
    })

    for (const [locale, messages] of locales) {
      if (locale === 'en') {
        continue
      }

      it(`${group}/${locale} translates every en key`, () => {
        const allowed = new Set(UNTRANSLATED[`${group}/${locale}`] ?? [])
        const missing = [...source.keys()].filter((key) => !messages.has(key) && !allowed.has(key))

        expect(missing).toEqual([])
      })

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
