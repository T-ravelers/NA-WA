import path from 'node:path'

const FEATURE_SEGMENT = `${path.sep}src${path.sep}features${path.sep}`
const SHARED_SEGMENT = `${path.sep}src${path.sep}shared${path.sep}`

function featureName(filePath) {
  const segmentIndex = filePath.indexOf(FEATURE_SEGMENT)

  if (segmentIndex < 0) {
    return null
  }

  const featurePath = filePath.slice(segmentIndex + FEATURE_SEGMENT.length)
  return featurePath.split(path.sep)[0] || null
}

function isRelativeImport(source) {
  return source === '.' || source === '..' || source.startsWith('./') || source.startsWith('../')
}

function importedFeatureName(source, importerPath) {
  if (source.startsWith('@/features/')) {
    return source.split('/')[2] || null
  }

  if (isRelativeImport(source)) {
    const importedPath = path.resolve(path.dirname(importerPath), source)
    return featureName(importedPath)
  }

  return null
}

function reportRestrictedImport(context, node, source) {
  if (typeof source.value !== 'string') {
    return
  }

  const importerPath = path.resolve(context.filename)
  const importedFeature = importedFeatureName(source.value, importerPath)

  if (importedFeature === null) {
    return
  }

  const importerFeature = featureName(importerPath)

  if (importerFeature !== null && importerFeature !== importedFeature) {
    context.report({ node, messageId: 'featureToFeature' })
    return
  }

  if (importerPath.includes(SHARED_SEGMENT)) {
    context.report({ node, messageId: 'sharedToFeature' })
  }
}

const noCrossFeatureImports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'alias 또는 상대경로로 Feature 경계를 넘는 import를 금지합니다.',
    },
    schema: [],
    messages: {
      featureToFeature:
        'Feature 간 직접 import는 금지합니다. app 주입 또는 shared 계약을 사용하세요.',
      sharedToFeature:
        'Shared는 Feature를 import할 수 없습니다. Feature 조합은 app에서 처리하세요.',
    },
  },
  create(context) {
    return {
      ImportDeclaration(node) {
        reportRestrictedImport(context, node, node.source)
      },
      ExportNamedDeclaration(node) {
        if (node.source !== null) {
          reportRestrictedImport(context, node, node.source)
        }
      },
      ExportAllDeclaration(node) {
        reportRestrictedImport(context, node, node.source)
      },
      ImportExpression(node) {
        reportRestrictedImport(context, node, node.source)
      },
    }
  },
}

export const featureBoundariesPlugin = {
  rules: {
    'no-cross-feature-imports': noCrossFeatureImports,
  },
}
