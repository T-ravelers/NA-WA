import { globalIgnores } from 'eslint/config'
import { withVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import pluginVue from 'eslint-plugin-vue'
import pluginPlaywright from 'eslint-plugin-playwright'
import pluginVitest from '@vitest/eslint-plugin'
import skipFormatting from 'eslint-config-prettier/flat'
import { designTokensPlugin } from './eslint-rules/design-tokens.mjs'
import { featureBoundariesPlugin } from './eslint-rules/feature-boundaries.mjs'

export default withVueTs(
  {
    rootDir: import.meta.dirname,
  },

  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
    linterOptions: {
      reportUnusedDisableDirectives: 'error',
    },
  },

  globalIgnores([
    '**/dist/**',
    '**/dist-ssr/**',
    '**/coverage/**',
    '**/playwright-report/**',
    '**/test-results/**',
    'eslint-rules/**/*.d.mts',
  ]),

  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommendedTypeChecked,

  {
    name: 'app/project-rules',
    rules: {
      curly: ['error', 'all'],
      eqeqeq: ['error', 'always'],
      'no-console': ['error', { allow: ['warn', 'error'] }],
      'vue/component-api-style': ['error', ['script-setup']],
      'vue/component-name-in-template-casing': ['error', 'PascalCase'],
      'vue/define-macros-order': 'error',
      'vue/html-button-has-type': 'error',
    },
  },

  {
    name: 'app/feature-boundaries',
    files: ['src/features/**/*.{ts,tsx,vue}'],
    plugins: {
      architecture: featureBoundariesPlugin,
    },
    rules: {
      'architecture/no-cross-feature-imports': 'error',
    },
  },

  {
    name: 'app/shared-boundaries',
    files: ['src/shared/**/*.{ts,tsx,vue}'],
    plugins: {
      architecture: featureBoundariesPlugin,
    },
    rules: {
      'architecture/no-cross-feature-imports': 'error',
    },
  },

  {
    name: 'app/design-token-rules',
    files: ['src/**/*.{ts,tsx,vue}'],
    ignores: ['src/**/__tests__/**', 'src/**/__fixtures__/**', 'src/**/*.{test,spec}.{ts,tsx}'],
    plugins: {
      'design-tokens': designTokensPlugin,
    },
    rules: {
      'design-tokens/no-raw-colors': 'error',
    },
  },

  {
    ...pluginPlaywright.configs['flat/recommended'],
    name: 'app/playwright-rules',
    files: ['e2e/**/*.{test,spec}.{js,ts,jsx,tsx}'],
  },

  {
    ...pluginVitest.configs.recommended,
    name: 'app/vitest-rules',
    files: ['src/**/__tests__/**/*.{ts,tsx}', 'src/**/*.{test,spec}.{ts,tsx}'],
  },

  {
    // 개발용 CLI 스크립트는 콘솔 출력이 목적이다. 앱 코드에는 이 예외를 적용하지 않는다.
    name: 'app/script-rules',
    files: ['scripts/**/*.{js,mjs,ts,mts}'],
    rules: {
      'no-console': 'off',
    },
  },

  skipFormatting,
)
