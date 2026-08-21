import { SUPPORTED_LOCALES, isSupportedLocale, type AppLocale } from '@/shared/i18n/locales'

/**
 * 로케일 메시지 수집.
 *
 * `shared/i18n/<locale>.ts`와 `features/<domain>/i18n/<locale>.ts`를 자동으로 모은다.
 * 문구를 추가할 때 이 파일이나 다른 공용 파일을 수정하지 않는다. 담당 feature 안에
 * 파일을 만들면 된다. 여러 명이 하나의 메시지 파일을 함께 고치는 상황을 만들지 않는다.
 *
 * 각 파일은 자기 네임스페이스를 최상위 key로 갖는 객체를 default export한다.
 * 네임스페이스는 feature 이름을 사용해 다른 feature와 겹치지 않게 한다.
 *
 * 예외는 오류 코드다. `resolveErrorMessageKey`가 코드 접두사로 키를 만들기 때문에,
 * 접두사가 feature 이름과 다르면(appointment 화면이 쓰는 `REVIEW-*`처럼) 그 접두사
 * 네임스페이스를 같은 파일에 함께 둔다. 화면의 소유는 그대로 feature에 있다.
 */
export type MessageTree = { [key: string]: string | MessageTree }

const messageModules = import.meta.glob<{ default: MessageTree }>(
  ['../../shared/i18n/*.ts', '../../features/*/i18n/*.ts'],
  { eager: true },
)

function isMessageTree(value: string | MessageTree): value is MessageTree {
  return typeof value !== 'string'
}

function mergeInto(target: MessageTree, source: MessageTree): void {
  for (const [key, sourceValue] of Object.entries(source)) {
    const targetValue = target[key]

    if (targetValue !== undefined && isMessageTree(targetValue) && isMessageTree(sourceValue)) {
      mergeInto(targetValue, sourceValue)
      continue
    }

    target[key] = sourceValue
  }
}

/** 파일 경로에서 로케일을 읽는다. 로케일 이름이 아닌 파일은 건너뛴다. */
function resolveLocale(path: string): AppLocale | null {
  const fileName = path.split('/').pop()?.replace(/\.ts$/, '')

  return fileName !== undefined && isSupportedLocale(fileName) ? fileName : null
}

export function buildMessages(): Record<AppLocale, MessageTree> {
  const messages = Object.fromEntries(SUPPORTED_LOCALES.map((locale) => [locale, {}])) as Record<
    AppLocale,
    MessageTree
  >

  for (const [path, module] of Object.entries(messageModules)) {
    const locale = resolveLocale(path)

    if (locale === null) {
      continue
    }

    mergeInto(messages[locale], module.default)
  }

  return messages
}
