import { toValue, type MaybeRefOrGetter } from 'vue'

/**
 * `role="radiogroup"`의 키보드 이동.
 *
 * 라디오 그룹은 화살표 키로 옮겨 다니고 **탭 스톱이 그룹당 하나**인 것이 전제다
 * (`tabindex`는 선택된 것만 `0`). 지금까지는 선택지가 전부 탭 순서에 들어가 있어서,
 * QR 결제 화면에 소비 카테고리 칩 일곱 개가 생기자 결제 버튼까지 가는 데 탭을 일곱 번
 * 더 눌러야 했다(#304 리뷰 → #305). `SegmentedControl`도 같은 상태였고 선택지가 둘이라
 * 비용이 드러나지 않았을 뿐이다.
 *
 * 화살표로 옮기면 **초점과 선택이 함께** 움직인다(WAI-ARIA radio group 패턴).
 * 끝에서는 반대쪽 끝으로 돌아간다.
 *
 * 초점 이동은 그룹 안에서 `[role="radio"]`를 순서대로 찾아 옮긴다. 컴포넌트마다 ref
 * 배열을 따로 들고 다니면 `v-for`가 바뀔 때 어긋나므로 DOM을 그대로 읽는다.
 */

/** 다음 초점으로 옮길지, 옮긴다면 몇 번째로 옮길지. */
function nextIndex(key: string, current: number, count: number): number | null {
  switch (key) {
    case 'ArrowRight':
    case 'ArrowDown':
      return (current + 1) % count
    case 'ArrowLeft':
    case 'ArrowUp':
      return (current - 1 + count) % count
    case 'Home':
      return 0
    case 'End':
      return count - 1
    default:
      return null
  }
}

interface RovingRadioGroup {
  /** 그룹 컨테이너의 `keydown`에 건다. */
  onKeydown: (event: KeyboardEvent) => void
  /** 선택된 것만 탭 스톱으로 남긴다. */
  tabindexFor: (value: string) => 0 | -1
}

export function useRovingRadioGroup(
  values: MaybeRefOrGetter<readonly string[]>,
  selected: MaybeRefOrGetter<string>,
  select: (value: string) => void,
): RovingRadioGroup {
  function onKeydown(event: KeyboardEvent): void {
    const list = toValue(values)
    if (list.length === 0) return

    /* 선택된 값이 목록에 없으면(초기 상태 등) 첫 번째를 기준으로 삼는다. */
    const current = Math.max(list.indexOf(toValue(selected)), 0)
    const target = nextIndex(event.key, current, list.length)
    if (target === null) return

    const nextValue = list[target]
    if (nextValue === undefined) return

    /* 화살표는 화면 스크롤도 일으킨다. 그룹 안에서는 이동만 하게 막는다. */
    event.preventDefault()
    select(nextValue)

    const container = event.currentTarget
    if (!(container instanceof HTMLElement)) return

    const radios = container.querySelectorAll<HTMLElement>('[role="radio"]')
    radios.item(target)?.focus()
  }

  function tabindexFor(value: string): 0 | -1 {
    const list = toValue(values)
    const current = toValue(selected)

    /* 선택된 값이 목록에 없으면 탭으로 그룹에 아예 못 들어가므로 첫 번째를 연다. */
    return value === current || (!list.includes(current) && value === list[0]) ? 0 : -1
  }

  return { onKeydown, tabindexFor }
}
