import { ESLint } from 'eslint'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const eslint = new ESLint({ cwd: path.resolve(import.meta.dirname, '../../..') })
const RULE_ID = 'architecture/no-cross-feature-imports'

async function lint(source: string, relativeFilePath: string) {
  const [result] = await eslint.lintText(source, {
    filePath: path.resolve(import.meta.dirname, '../../..', relativeFilePath),
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
  }, 20_000)

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
