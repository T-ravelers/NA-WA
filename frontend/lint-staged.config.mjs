/** @type {import('lint-staged').Configuration} */
export default {
  '*.{ts,mts,tsx,vue}': [
    'frontend/node_modules/.bin/eslint --fix --cache --cache-location frontend/.eslintcache --max-warnings=0 --no-warn-ignored',
    'frontend/node_modules/.bin/prettier --write',
  ],
  '*.{js,jsx,mjs,cjs,css,scss,sass,less,html,json,jsonc,md,yml,yaml}':
    'frontend/node_modules/.bin/prettier --write',
}
