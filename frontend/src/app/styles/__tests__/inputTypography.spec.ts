import { globSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

const packageRoot = process.cwd()
const sourceRoot = join(packageRoot, 'src')
const tokensCss = readFileSync(join(sourceRoot, 'app/styles/tokens.css'), 'utf8')

const textSizes = new Map(
  [...tokensCss.matchAll(/--text-([a-z0-9-]+):\s*(\d+(?:\.\d+)?)px;/g)].map((match) => [
    match[1] as string,
    Number(match[2]),
  ]),
)

const NON_TEXT_INPUT_TYPES = new Set([
  'button',
  'checkbox',
  'color',
  'file',
  'hidden',
  'image',
  'radio',
  'range',
  'reset',
  'submit',
])

interface FormControl {
  file: string
  tag: string
}

function formControls(): FormControl[] {
  return globSync('**/*.vue', { cwd: sourceRoot }).flatMap((file) => {
    const source = readFileSync(join(sourceRoot, file), 'utf8')
    const templateStart = source.indexOf('>', source.indexOf('<template')) + 1
    const templateEnd = source.lastIndexOf('</template>')

    if (templateStart === 0 || templateEnd === -1) return []

    const template = source.slice(templateStart, templateEnd)
    const withoutComments = template.replace(/<!--[\s\S]*?-->/g, '')

    return [...withoutComments.matchAll(/<(?:input|select|textarea)\b[\s\S]*?>/g)]
      .map(([tag]) => ({ file, tag }))
      .filter(({ tag }) => {
        const type = /\btype=["']([^"']+)["']/.exec(tag)?.[1]?.toLowerCase()
        return type === undefined || !NON_TEXT_INPUT_TYPES.has(type)
      })
  })
}

function fontSizeOf(tag: string): number | null {
  const classes = /\bclass=["']([^"']+)["']/.exec(tag)?.[1] ?? ''

  for (const match of classes.matchAll(/(?:^|\s)text-([a-z0-9-]+)(?=\s|$)/g)) {
    const size = textSizes.get(match[1] as string)
    if (size !== undefined) return size
  }

  return null
}

describe('form control typography', () => {
  it('keeps every text-entry control at least 16px to prevent iOS focus zoom', () => {
    const violations = formControls()
      .map(({ file, tag }) => ({ file, size: fontSizeOf(tag), tag }))
      .filter(({ size }) => size === null || size < 16)

    expect(violations).toEqual([])
  })

  it('keeps intentional page zoom available in the viewport contract', () => {
    const indexHtml = readFileSync(join(packageRoot, 'index.html'), 'utf8')
    const viewport = /<meta\s+name=["']viewport["'][^>]*>/i.exec(indexHtml)?.[0]

    expect(viewport).toBeDefined()
    expect(viewport).not.toMatch(/maximum-scale|user-scalable\s*=\s*no/i)
  })
})
