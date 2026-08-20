import { onBeforeUnmount, onMounted, type ShallowRef } from 'vue'

/**
 * 모달·시트를 ESC로 닫고, 열 때 안으로 포커스를 넣고 닫을 때 열었던 곳으로
 * 되돌린다.
 *
 * 열려 있는 동안 뒤 화면에 포커스가 남아 있으면 스크린 리더·키보드 사용자가
 * 보이지 않는 요소를 밟게 되고, 닫은 뒤 포커스가 body로 떨어지면 방금 누른
 * 버튼으로 돌아갈 방법이 없다.
 *
 * `features/settlement`의 `SettlementBottomSheet`가 같은 동작을 컴포넌트 안에
 * 직접 들고 있다. feature끼리는 import하지 않으므로 여기서는 따로 둔다.
 * 두 곳을 합칠 `shared/ui` 승격은 별도 작업이다.
 */
export function useOverlayDismiss(
  container: Readonly<ShallowRef<HTMLElement | null>>,
  onDismiss: () => void,
): void {
  let previousFocus: HTMLElement | null = null

  function handleKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') onDismiss()
  }

  onMounted(() => {
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    window.addEventListener('keydown', handleKeydown)
    container.value?.querySelector<HTMLElement>('button:not([disabled]), [tabindex="0"]')?.focus()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('keydown', handleKeydown)
    previousFocus?.focus()
  })
}
