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
 * 초점은 그룹 안에서 **`data-value`가 맞는 칩**을 찾아 옮긴다. 순서로 찾으면 「DOM 순서 =
 * `values` 순서」가 조용한 전제가 되어, 옵션에 `v-if`가 붙는 순간 선택은 맞는 값으로 가고
 * 초점만 다른 칩으로 간다. 컴포넌트마다 ref 배열을 들고 다니지 않으려고 DOM을 읽되,
 * **무엇을 읽는지는 값으로 못 박는다.** 부르는 쪽은 각 칩에 `:data-value`를 준다.
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

interface RovingRadioGroup<T extends string> {
  /** 그룹 컨테이너의 `keydown`에 건다. */
  onKeydown: (event: KeyboardEvent) => void
  /** 선택된 것만 탭 스톱으로 남긴다. */
  tabindexFor: (value: T) => 0 | -1
}

/**
 * `T`로 열어 두면 호출부가 자기 유니온(`SpendingCategory`·`SettlementType`)을 그대로
 * 넘기고 돌려받는다. `string`으로 좁혀 두면 돌려받은 값을 매번 캐스트해야 하고, 목록에
 * 없는 값을 넣어도 컴파일이 잡아 주지 못한다.
 */
export function useRovingRadioGroup<T extends string>(
  values: MaybeRefOrGetter<readonly T[]>,
  selected: MaybeRefOrGetter<T>,
  select: (value: T) => void,
): RovingRadioGroup<T> {
  function onKeydown(event: KeyboardEvent): void {
    /*
     * 수식 키가 붙은 화살표는 그룹의 것이 아니다.
     *
     * macOS `Cmd + ←`와 Windows·Linux `Alt + ←`는 브라우저 뒤로 가기다. 칩에 초점이
     * 있을 때 이것을 가로채면 아래 `preventDefault()`가 뒤로 가기를 막고 엉뚱하게
     * 선택만 바뀐다. APG 구현들이 수식 키가 붙은 이벤트를 흘려보내는 이유가 이것이다.
     */
    if (event.metaKey || event.ctrlKey || event.altKey || event.shiftKey) return

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

    /*
     * 선택자에 값을 끼워 넣지 않는다. 이스케이프가 필요해지고(`CSS.escape`는 jsdom에
     * 없다) 값에 따옴표가 들어가면 조용히 아무것도 못 찾는다. 읽어서 비교한다.
     */
    const radios = container.querySelectorAll<HTMLElement>('[role="radio"]')
    Array.from(radios)
      .find((radio) => radio.dataset.value === nextValue)
      ?.focus()
  }

  function tabindexFor(value: T): 0 | -1 {
    const list = toValue(values)
    const current = toValue(selected)

    /* 선택된 값이 목록에 없으면 탭으로 그룹에 아예 못 들어가므로 첫 번째를 연다. */
    return value === current || (!list.includes(current) && value === list[0]) ? 0 : -1
  }

  return { onKeydown, tabindexFor }
}
