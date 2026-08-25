import { useReducedMotion } from 'motion-v'
import { computed, onMounted, ref } from 'vue'

const TAB_CONTENT_DURATION_SECONDS = 0.18

/**
 * 세그먼트 아래 콘텐츠에만 적용하는 짧은 페이드다.
 *
 * 두 화면 모두 목록·빈 상태처럼 높이가 크게 달라질 수 있어 나가는 콘텐츠를 겹쳐 두지 않고
 * 들어오는 쪽만 페이드한다. 첫 렌더와 감소 모션 설정에서는 즉시 최종 상태를 보여 준다.
 */
export function useTabContentMotion() {
  const isMounted = ref(false)
  const reducedMotion = useReducedMotion()

  onMounted(() => {
    isMounted.value = true
  })

  return computed(() => {
    const shouldAnimate = isMounted.value && !reducedMotion.value

    return {
      initial: shouldAnimate ? { opacity: 0 } : false,
      animate: { opacity: 1 },
      transition: {
        type: 'tween' as const,
        duration: shouldAnimate ? TAB_CONTENT_DURATION_SECONDS : 0,
        ease: 'easeOut' as const,
      },
    }
  })
}
