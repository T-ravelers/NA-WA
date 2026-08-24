import { vueTsConfigs } from '@vue/eslint-config-typescript'
import { ESLint, type Linter } from 'eslint'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import vueParser from 'vue-eslint-parser'

import { designTokensPlugin } from '../../../eslint-rules/design-tokens.mjs'

const projectRoot = path.resolve(import.meta.dirname, '../../..')
const RULE_ID = 'design-tokens/no-raw-colors'
const ERROR_SEVERITY = [2]

const [typescriptBaseConfig] = [vueTsConfigs.base].flat()
const typescriptParser = typescriptBaseConfig?.languageOptions?.parser as Linter.Parser | undefined

if (typescriptParser === undefined) {
  throw new Error('Could not read the TypeScript parser from @vue/eslint-config-typescript.')
}

const ruleOnlyEslint = new ESLint({
  cwd: projectRoot,
  overrideConfigFile: true,
  overrideConfig: [
    {
      files: ['**/*.{ts,tsx}'],
      languageOptions: { parser: typescriptParser, sourceType: 'module' },
      plugins: { 'design-tokens': designTokensPlugin },
      rules: { [RULE_ID]: 'error' },
    },
    {
      files: ['**/*.vue'],
      languageOptions: {
        parser: vueParser,
        parserOptions: { parser: typescriptParser, sourceType: 'module' },
      },
      plugins: { 'design-tokens': designTokensPlugin },
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
  if (result.messages.some((message) => message.ruleId === null)) {
    throw new Error(`No config matched ${relativeFilePath}. The rule did not run.`)
  }

  return result.messages.filter((message) => message.ruleId === RULE_ID)
}

describe('Design token ESLint rule', () => {
  it('rejects raw HEX in TypeScript strings and template literals', async () => {
    const messages = await lint(
      "const direct = '#abcdef'\nconst gradient = `linear-gradient(#000, ${direct})`",
      'src/shared/ui/colors.ts',
    )

    expect(messages).toHaveLength(2)
  })

  it('rejects arbitrary colors in Vue classes regardless of color syntax', async () => {
    const messages = await lint(
      '<template><div class="bg-[#aaa8a3] text-[rgb(1_2_3)] border-[var(--raw)]" /></template>',
      'src/shared/ui/RawColor.vue',
    )

    expect(messages).toHaveLength(1)
    expect(messages[0]?.message).toContain('arbitrary 색상')
  })

  it('rejects arbitrary colors in dynamic Vue class expressions', async () => {
    const messages = await lint(
      `<template><div :class="'fill-[color:var(--raw)]'" /></template>`,
      'src/shared/ui/DynamicRawColor.vue',
    )

    expect(messages).toHaveLength(1)
  })

  it('rejects CSS named colors in arbitrary color utilities', async () => {
    const messages = await lint(
      '<template><div class="bg-[red] text-[rebeccapurple] border-[gold] fill-[color:tomato]" /></template>',
      'src/shared/ui/NamedRawColor.vue',
    )

    expect(messages).toHaveLength(1)
    expect(messages[0]?.message).toContain('arbitrary 색상')
  })

  it('rejects arbitrary colors with current and deprecated important modifiers', async () => {
    const classNames = [
      'bg-[red]!',
      'hover:text-[rgb(1_2_3)]!',
      'border-[var(--raw)]!',
      '!bg-[red]',
      'hover:!text-[rgb(1_2_3)]',
    ]

    for (const [index, className] of classNames.entries()) {
      const messages = await lint(
        `<template><div class="${className}" /></template>`,
        `src/shared/ui/ImportantRawColor${index}.vue`,
      )

      expect(messages).toHaveLength(1)
      expect(messages[0]?.message).toContain('arbitrary 색상')
    }
  })

  it('rejects raw colors in arbitrary gradient utilities', async () => {
    const classNames = [
      'bg-linear-[25deg,red_5%,yellow_60%,lime_90%]',
      'hover:bg-radial-[circle,var(--raw)_0%,transparent_100%]!',
      'bg-conic-[from_45deg_at_50%_50%,tomato,blue]',
    ]

    for (const [index, className] of classNames.entries()) {
      const messages = await lint(
        `<template><div class="${className}" /></template>`,
        `src/shared/ui/GradientRawColor${index}.vue`,
      )

      expect(messages).toHaveLength(1)
      expect(messages[0]?.message).toContain('arbitrary 색상')
    }
  })

  it('rejects raw colors embedded in composite values and arbitrary variants', async () => {
    const classNames = [
      'bg-[linear-gradient(to_right,red,blue)]',
      'shadow-[0_0_2px_rgb(1_2_3)]',
      'outline-[2px_solid_red]',
      'drop-shadow-[0_4px_4px_rgb(1_2_3)]',
      '[&::before]:bg-[red]',
    ]

    for (const [index, className] of classNames.entries()) {
      const messages = await lint(
        `<template><div class="${className}" /></template>`,
        `src/shared/ui/CompositeRawColor${index}.vue`,
      )

      expect(messages).toHaveLength(1)
      expect(messages[0]?.message).toContain('arbitrary 색상')
    }
  })

  it('rejects raw HEX in Vue style blocks', async () => {
    const messages = await lint(
      '<template><div class="sample" /></template><style scoped>.sample { color: #fff; }</style>',
      'src/shared/ui/RawStyle.vue',
    )

    expect(messages).toHaveLength(1)
  })

  it('allows token classes, CSS variables, non-color arbitrary values, URLs, and comments', async () => {
    const messages = await lint(
      `
        <template>
          <!-- 대비 근거 #ffffff -->
          <div class="border-[1.5px] bg-[url('/assets/red/paper.png')] bg-paper text-ink" />
        </template>
        <script setup lang="ts">
        // 과거 값 #000000
        const color = 'var(--color-ink)'
        </script>
        <style scoped>
        /* 과거 값 #ffffff */
        .sample { color: var(--color-ink); }
        </style>
      `,
      'src/shared/ui/TokenColor.vue',
    )

    expect(messages).toHaveLength(0)
  })
})

describe('Design token rule wiring', () => {
  it('applies only to production TypeScript and Vue source', async () => {
    const projectEslint = new ESLint({ cwd: projectRoot })
    const configFor = (relativeFilePath: string) =>
      projectEslint.calculateConfigForFile(path.resolve(projectRoot, relativeFilePath))

    const productionTs = await configFor('src/shared/lib/money.ts')
    const productionVue = await configFor('src/shared/ui/AppTicket.vue')
    const unitTest = await configFor('src/shared/ui/__tests__/AppTicket.spec.ts')
    const fixture = await configFor(
      'src/features/report/components/presentation/__fixtures__/reportPresentation.ts',
    )

    expect(productionTs.rules?.[RULE_ID]).toEqual(ERROR_SEVERITY)
    expect(productionVue.rules?.[RULE_ID]).toEqual(ERROR_SEVERITY)
    expect(unitTest.rules?.[RULE_ID]).toBeUndefined()
    expect(fixture.rules?.[RULE_ID]).toBeUndefined()
  })
})
