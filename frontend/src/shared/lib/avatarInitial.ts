interface GraphemeSegment {
  segment: string
}

interface GraphemeSegmenter {
  segment(value: string): Iterable<GraphemeSegment>
}

type GraphemeSegmenterConstructor = new (
  locales?: string | string[],
  options?: { granularity: 'grapheme' },
) => GraphemeSegmenter

let graphemeSegmenter: GraphemeSegmenter | null | undefined

function getGraphemeSegmenter(): GraphemeSegmenter | null {
  if (graphemeSegmenter !== undefined) {
    return graphemeSegmenter
  }

  const Segmenter = (Intl as unknown as { Segmenter?: GraphemeSegmenterConstructor }).Segmenter
  graphemeSegmenter =
    Segmenter === undefined ? null : new Segmenter(undefined, { granularity: 'grapheme' })

  return graphemeSegmenter
}

function getFirstGrapheme(value: string): string | undefined {
  const segments = getGraphemeSegmenter()?.segment(value)

  return segments?.[Symbol.iterator]().next().value?.segment ?? [...value][0]
}

/**
 * 표시명에서 아바타 폴백 한 글자를 만든다.
 *
 * `Intl.Segmenter`가 있으면 국기와 ZWJ 조합 이모지도 사용자가 보는 한 글자로 유지한다.
 * 대문자 변환 결과도 다시 한 글자로 제한해 `ß`처럼 여러 글자로 확장되는 입력을 처리한다.
 * 오래된 브라우저에서는 코드 포인트 단위로 폴백해 최소한 서로게이트 페어가 깨지지 않게 한다.
 */
export function getAvatarInitial(displayName: string): string {
  const normalized = displayName.trim()

  if (normalized === '') {
    return '?'
  }

  const first = getFirstGrapheme(normalized)
  if (first === undefined) {
    return '?'
  }

  return getFirstGrapheme(first.toUpperCase()) ?? '?'
}
