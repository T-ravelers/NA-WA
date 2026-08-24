import { readdirSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'

import { describe, expect, it } from 'vitest'

const SOURCE_ROOT = join(process.cwd(), 'src')

/** 새 시트가 생겨도 목록을 손으로 갱신하지 않고 같은 바탕 규칙에 들어오게 전수조사한다. */
function vueFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)

    if (entry.isDirectory()) return vueFiles(path)
    return entry.isFile() && entry.name.endsWith('.vue') ? [path] : []
  })
}

/** 바닥에 닿고 위 모서리만 둥근 dialog를 바텀시트로 센다(#477). */
function bottomSheetClass(source: string): string | null {
  const match =
    /<(?:section|div)\b(?=[^>]*\brole="dialog")(?=[^>]*\bclass="([^"]*\brounded-t-(?:lg|card)\b[^"]*)")[^>]*>/.exec(
      source,
    )

  return match?.[1] ?? null
}

const bottomSheets = vueFiles(SOURCE_ROOT)
  .map((path) => ({
    path: relative(SOURCE_ROOT, path),
    className: bottomSheetClass(readFileSync(path, 'utf8')),
  }))
  .filter((sheet): sheet is { path: string; className: string } => sheet.className !== null)
  .sort((a, b) => a.path.localeCompare(b.path))

const EXPECTED_BOTTOM_SHEETS = [
  'features/appointment/components/AppointmentJourneyDateSheet.vue',
  'features/appointment/components/AppointmentJourneySelectSheet.vue',
  'features/appointment/components/AppointmentMenuSheet.vue',
  'features/explore/components/ExploreFilterSheet.vue',
  'features/explore/components/JourneyDateSheet.vue',
  'features/explore/components/JourneySelectSheet.vue',
  'features/explore/components/PlaceFilterSheet.vue',
  'features/journey/components/JourneyDateRangePicker.vue',
  'features/settlement/components/SettlementBottomSheet.vue',
  'shared/ui/LocaleSheet.vue',
]

describe('bottom-sheet surfaces', () => {
  it('covers every bottom-aligned dialog with rounded top corners', () => {
    expect(bottomSheets.map((sheet) => sheet.path)).toEqual(EXPECTED_BOTTOM_SHEETS)
  })

  it.each(bottomSheets)('$path uses the canvas surface', ({ className }) => {
    expect(className.split(/\s+/)).toContain('bg-canvas')
  })
})
