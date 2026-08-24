const RAW_HEX_COLOR = /#(?:[\da-f]{8}|[\da-f]{6}|[\da-f]{4}|[\da-f]{3})(?![\da-f])/i

const COLOR_UTILITY_PATTERN = String.raw`(?:bg(?:-(?:linear|radial|conic))?|text|border(?:-(?:[trblxyse]|bs|be))?|divide(?:-[xy])?|ring(?:-offset)?|inset-ring|outline|shadow|inset-shadow|text-shadow|drop-shadow|filter|backdrop-filter|mask(?:-(?:linear|radial|conic))?|list-image|scrollbar-(?:thumb|track)|fill|stroke|caret|accent|decoration|placeholder|from|via|to)`

const ARBITRARY_COLOR_UTILITY = new RegExp(
  String.raw`^!?${COLOR_UTILITY_PATTERN}-\[(.+)\](?:\/[^\s!]+)?!?$`,
  'i',
)

const CUSTOM_PROPERTY_COLOR_UTILITY = new RegExp(
  String.raw`^!?${COLOR_UTILITY_PATTERN}-\((?:[a-z-]+:)?--[^)]+\)(?:\/[^\s!]+)?!?$`,
  'i',
)

const ARBITRARY_PROPERTY = /^!?\[((?:--|-)?[a-z][a-z\d_-]*):(.+)\](?:\/[^\s!]+)?!?$/i

const COLOR_FUNCTION =
  /(?:^|[^a-z\d-])(?:#|(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color|color-mix|device-cmyk|light-dark|var)\()/i
const CSS_NAMED_COLORS = new Set(
  `aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown burlywood cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen darkslateblue darkslategray darkslategrey darkturquoise darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray green greenyellow grey honeydew hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen lightskyblue lightslategray lightslategrey lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive olivedrab orange orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver skyblue slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato transparent turquoise violet wheat white whitesmoke yellow yellowgreen currentcolor`.split(
    ' ',
  ),
)
const CSS_SYSTEM_COLORS = new Set(
  `accentcolor accentcolortext activetext buttonborder buttonface buttontext canvas canvastext field fieldtext graytext highlight highlighttext linktext mark marktext selecteditem selecteditemtext visitedtext activeborder activecaption appworkspace background buttonhighlight buttonshadow captiontext inactiveborder inactivecaption inactivecaptiontext infobackground infotext menu menutext scrollbar threedarkshadow threedface threedhighlight threedlightshadow threedshadow window windowframe windowtext`.split(
    ' ',
  ),
)

const COLOR_CAPABLE_CSS_PROPERTIES = new Set([
  'background',
  'background-image',
  'backdrop-filter',
  'border',
  'border-block',
  'border-block-end',
  'border-block-start',
  'border-bottom',
  'border-image',
  'border-image-source',
  'border-inline',
  'border-inline-end',
  'border-inline-start',
  'border-left',
  'border-right',
  'border-top',
  'box-shadow',
  'column-rule',
  'fill',
  'filter',
  'list-style-image',
  'mask',
  'mask-image',
  'outline',
  'stroke',
  'shape-outside',
  'text-decoration',
  'text-emphasis',
  'text-shadow',
  'text-stroke',
])

function blankUrlFunctions(value) {
  const characters = value.split('')

  for (let start = 0; start < value.length - 3; start += 1) {
    if (value.slice(start, start + 4).toLowerCase() !== 'url(') {
      continue
    }

    let depth = 1
    let quote = null
    let escaped = false
    let end = start + 4

    for (; end < value.length && depth > 0; end += 1) {
      const character = value[end]
      if (escaped) {
        escaped = false
      } else if (character === '\\') {
        escaped = true
      } else if (quote !== null) {
        if (character === quote) {
          quote = null
        }
      } else if (character === "'" || character === '"') {
        quote = character
      } else if (character === '(') {
        depth += 1
      } else if (character === ')') {
        depth -= 1
      }
    }

    characters.fill(' ', start, end)
    start = end - 1
  }

  return characters.join('')
}

function containsRawColor(value) {
  const withoutUrls = blankUrlFunctions(value)
  if (COLOR_FUNCTION.test(withoutUrls)) {
    return true
  }

  return withoutUrls.split(/[^a-z\d-]+/i).some((token) => {
    const normalized = token.toLowerCase()
    return CSS_NAMED_COLORS.has(normalized) || CSS_SYSTEM_COLORS.has(normalized)
  })
}

function isColorCapableProperty(property) {
  const normalized = property.toLowerCase()
  const unprefixed = normalized.replace(/^-(?:webkit|moz|ms|o)-/, '')
  return (
    normalized.startsWith('--') ||
    unprefixed.endsWith('color') ||
    COLOR_CAPABLE_CSS_PROPERTIES.has(unprefixed)
  )
}

function withoutVariants(className) {
  let bracketDepth = 0
  let parenthesisDepth = 0
  let lastVariantSeparator = -1
  let escaped = false

  for (let index = 0; index < className.length; index += 1) {
    const character = className[index]
    if (escaped) {
      escaped = false
    } else if (character === '\\') {
      escaped = true
    } else if (character === '[') {
      bracketDepth += 1
    } else if (character === ']') {
      bracketDepth = Math.max(0, bracketDepth - 1)
    } else if (character === '(') {
      parenthesisDepth += 1
    } else if (character === ')') {
      parenthesisDepth = Math.max(0, parenthesisDepth - 1)
    } else if (character === ':' && bracketDepth === 0 && parenthesisDepth === 0) {
      lastVariantSeparator = index
    }
  }

  return className.slice(lastVariantSeparator + 1)
}

function containsArbitraryColorUtility(value) {
  for (const className of value.split(/\s+/)) {
    const utility = withoutVariants(className)
    const colorMatch = ARBITRARY_COLOR_UTILITY.exec(utility)
    if (typeof colorMatch?.[1] === 'string' && containsRawColor(colorMatch[1])) {
      return true
    }

    if (CUSTOM_PROPERTY_COLOR_UTILITY.test(utility)) {
      return true
    }

    const propertyMatch = ARBITRARY_PROPERTY.exec(utility)
    if (
      typeof propertyMatch?.[1] === 'string' &&
      typeof propertyMatch[2] === 'string' &&
      isColorCapableProperty(propertyMatch[1]) &&
      containsRawColor(propertyMatch[2])
    ) {
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
