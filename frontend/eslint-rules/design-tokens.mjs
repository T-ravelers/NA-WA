const RAW_HEX_COLOR = /#(?:[\da-f]{8}|[\da-f]{6}|[\da-f]{4}|[\da-f]{3})(?![\da-f])/i

const ARBITRARY_COLOR_UTILITY = new RegExp(
  String.raw`(?:^|\s)(?:[^\s:]+:)*(?:bg|text|border(?:-[trblxyse])?|divide(?:-[xy])?|ring(?:-offset)?|outline|shadow|fill|stroke|caret|accent|decoration|placeholder|from|via|to)-\[([^\]]+)\](?:\/[^\s]+)?(?=$|\s)`,
  'gi',
)

const COLOR_FUNCTION =
  /^(?:#|(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color|color-mix|light-dark|var)\()/i
const COLOR_KEYWORD = /^(?:transparent|currentcolor|black|white)$/i

function isArbitraryColor(value) {
  const normalized = value.trim().replace(/^color:/i, '')
  return COLOR_FUNCTION.test(normalized) || COLOR_KEYWORD.test(normalized)
}

function containsArbitraryColorUtility(value) {
  ARBITRARY_COLOR_UTILITY.lastIndex = 0

  for (const match of value.matchAll(ARBITRARY_COLOR_UTILITY)) {
    if (typeof match[1] === 'string' && isArbitraryColor(match[1])) {
      return true
    }
  }

  return false
}

function reportString(context, node, value) {
  if (containsArbitraryColorUtility(value)) {
    context.report({ node, messageId: 'arbitraryColor' })
    return
  }

  if (RAW_HEX_COLOR.test(value)) {
    context.report({ node, messageId: 'rawHex' })
  }
}

function blankCssComments(value) {
  return value.replace(/\/\*[\s\S]*?\*\//g, (comment) => ' '.repeat(comment.length))
}

function reportVueStyleHex(context) {
  const sourceCode = context.sourceCode
  const styleBlock = /<style\b[^>]*>([\s\S]*?)<\/style>/gi

  for (const block of sourceCode.text.matchAll(styleBlock)) {
    const content = block[1]
    if (typeof content !== 'string' || block.index === undefined) {
      continue
    }

    const contentOffset = block.index + block[0].indexOf(content)
    const withoutComments = blankCssComments(content)
    const match = RAW_HEX_COLOR.exec(withoutComments)

    if (match?.index !== undefined) {
      const start = sourceCode.getLocFromIndex(contentOffset + match.index)
      const end = sourceCode.getLocFromIndex(contentOffset + match.index + match[0].length)
      context.report({ loc: { start, end }, messageId: 'rawHex' })
    }
  }
}

const noRawColors = {
  meta: {
    type: 'problem',
    docs: {
      description: '생산 코드의 원시 HEX와 Tailwind arbitrary 색상을 금지합니다.',
    },
    schema: [],
    messages: {
      rawHex: '원시 HEX 색상 대신 tokens.css에서 생성된 디자인 토큰을 사용하세요.',
      arbitraryColor: 'Tailwind arbitrary 색상 대신 이름 있는 디자인 토큰을 사용하세요.',
    },
  },
  create(context) {
    const scriptVisitor = {
      Literal(node) {
        if (typeof node.value === 'string') {
          reportString(context, node, node.value)
        }
      },
      TemplateElement(node) {
        reportString(context, node, node.value.raw)
      },
      'Program:exit'() {
        if (context.filename.endsWith('.vue')) {
          reportVueStyleHex(context)
        }
      },
    }

    const templateVisitor = {
      VLiteral(node) {
        if (typeof node.value === 'string') {
          reportString(context, node, node.value)
        }
      },
      Literal(node) {
        if (typeof node.value === 'string') {
          reportString(context, node, node.value)
        }
      },
      TemplateElement(node) {
        reportString(context, node, node.value.raw)
      },
    }

    const defineTemplateBodyVisitor = context.sourceCode.parserServices.defineTemplateBodyVisitor
    return typeof defineTemplateBodyVisitor === 'function'
      ? defineTemplateBodyVisitor(templateVisitor, scriptVisitor)
      : scriptVisitor
  },
}

export const designTokensPlugin = {
  rules: {
    'no-raw-colors': noRawColors,
  },
}
