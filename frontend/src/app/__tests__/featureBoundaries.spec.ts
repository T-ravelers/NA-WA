import { vueTsConfigs } from '@vue/eslint-config-typescript'
import { ESLint, type Linter } from 'eslint'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

import { featureBoundariesPlugin } from '../../../eslint-rules/feature-boundaries.mjs'

const projectRoot = path.resolve(import.meta.dirname, '../../..')
const RULE_ID = 'architecture/no-cross-feature-imports'

/** ESLint가 정규화한 심각도. 설정에 `'error'`로 적은 규칙은 `[2]`로 읽힌다. */
const ERROR_SEVERITY = [2]

/**
 * TypeScript 파서만 꺼내 쓴다.
 *
 * `@typescript-eslint/parser`는 이 패키지의 전이 의존이라 pnpm 배치에서 직접 import할 수
 * 없다. 캐스팅이 필요한 이유는 두 패키지가 **같은 파서 객체를 서로 다른 타입으로 선언**해서
 * 구조가 어긋나기 때문이고, 런타임에 넘어가는 값은 하나다.
 */
const [typescriptBaseConfig] = [vueTsConfigs.base].flat()
const typescriptParser = typescriptBaseConfig?.languageOptions?.parser as Linter.Parser | undefined

if (typescriptParser === undefined) {
  throw new Error('Could not read the TypeScript parser from @vue/eslint-config-typescript.')
}

/**
 * 규칙 로직만 보는 가벼운 인스턴스.
 *
 * 프로젝트 설정을 그대로 쓰면 `recommendedTypeChecked` 때문에 `projectService: true`가
 * 되어, 코드 한 줄을 린트하려고 저장소 전체의 타입 정보를 세운다(첫 호출 12초).
 * 그런데 이 규칙은 경로 문자열만 비교하므로 타입 정보가 필요 없다.
 *
 * 파서만 얹고 타입 정보는 요구하지 않으면 `import type` 같은 TypeScript 문법을 파싱하면서도
 * 프로그램을 세우지 않아 검사당 수십 ms로 끝난다.
 *
 * 대신 이 인스턴스는 규칙이 실제 설정에 배선돼 있는지를 모른다. 그건 아래 별도 describe가
 * 확인한다 — 나눠 두지 않으면 로직 6건이 매번 배선 비용을 함께 낸다.
 */
const ruleOnlyEslint = new ESLint({
  cwd: projectRoot,
  overrideConfigFile: true,
  overrideConfig: [
    {
      files: ['**/*.ts'],
      languageOptions: { parser: typescriptParser, sourceType: 'module' },
      plugins: { architecture: featureBoundariesPlugin },
      rules: { [RULE_ID]: 'error' },
    },
  ],
})

async function lint(source: string, relativeFilePath: string) {
  const [result] = await ruleOnlyEslint.lintText(source, {
    filePath: path.resolve(projectRoot, relativeFilePath),
  })

  if (result === undefined) {
    throw new Error('ESLint returned no result')
  }

  return result.messages
}

describe('Feature boundary ESLint rule', () => {
  it('rejects a relative import into another Feature', async () => {
    const messages = await lint(
      "import { addJourneyItem } from '../journey/api/journeyApi'",
      'src/features/explore/routes.ts',
    )

    expect(messages.map((message) => message.ruleId)).toContain(RULE_ID)
  })

  it('rejects a type-only relative import into another Feature', async () => {
    const messages = await lint(
      "import type { MemberProfile } from '../member/api/memberApi'",
      'src/features/explore/routes.ts',
    )

    expect(messages.map((message) => message.ruleId)).toContain(RULE_ID)
  })

  it('allows a relative import inside the same Feature', async () => {
    const messages = await lint(
      "import type { EventDetail } from './model/eventDetail'",
      'src/features/explore/routes.ts',
    )

    expect(messages.filter((message) => message.ruleId === RULE_ID)).toHaveLength(0)
  })

  it('rejects a relative import from shared into a Feature', async () => {
    const messages = await lint(
      "export { fetchJourneys } from '../../features/journey/api/journeyApi'",
      'src/shared/api/httpClient.ts',
    )

    expect(messages.map((message) => message.ruleId)).toContain(RULE_ID)
  })

  it('rejects cross-Feature alias imports with the same rule', async () => {
    const messages = await lint(
      "import type { Journey } from '@/features/journey/api/journeyApi'",
      'src/features/explore/routes.ts',
    )

    expect(messages.map((message) => message.ruleId)).toContain(RULE_ID)
  })

  it('rejects a dynamic import into another Feature', async () => {
    const messages = await lint(
      "void import('@/features/journey/routes')",
      'src/features/explore/routes.ts',
    )

    expect(messages.map((message) => message.ruleId)).toContain(RULE_ID)
  })
})

/**
 * 위 검사들은 규칙을 직접 세워 돌리므로, 규칙이 **실제 프로젝트 설정에 걸려 있는지**는
 * 증명하지 못한다. 여기서 그것만 따로 본다.
 *
 * `eslint.config.ts`를 읽어야 하지만 `calculateConfigForFile`은 설정을 계산할 뿐 타입
 * 정보를 세우지 않고, 무거운 플러그인은 위 인스턴스가 이미 불러왔다. 그래서 0.5초 안쪽이다.
 */
describe('Feature boundary rule wiring', () => {
  it('applies the rule to Features and shared, and nowhere else', async () => {
    const projectEslint = new ESLint({ cwd: projectRoot })
    const configFor = (relativeFilePath: string) =>
      projectEslint.calculateConfigForFile(path.resolve(projectRoot, relativeFilePath))

    const featureConfig = await configFor('src/features/explore/routes.ts')
    const sharedConfig = await configFor('src/shared/api/httpClient.ts')
    const outsideConfig = await configFor('src/main.ts')

    expect(featureConfig.rules?.[RULE_ID]).toEqual(ERROR_SEVERITY)
    expect(sharedConfig.rules?.[RULE_ID]).toEqual(ERROR_SEVERITY)
    expect(outsideConfig.rules?.[RULE_ID]).toBeUndefined()
  })
})
