<script setup lang="ts">
import { vFitText } from '@/shared/lib/fitText'

/**
 * 티켓 우하단의 원형 도장.
 *
 * 시안 실측: 지름 64, 2px 보더, opacity 70%, -8° 회전.
 *
 * 상태를 장식으로 한 번 더 말하는 요소다. 같은 정보를 상태 뱃지가 텍스트로 이미
 * 전달하므로 접근성 트리에서는 감춘다. 스탬프만으로 상태를 표현하지 않는다.
 */
type StampTone = 'onPaper' | 'onDark'

interface Props {
  /** 도장 문구. 영문은 대문자로 들어온다고 가정하지 않고 여기서 강제한다. */
  label: string
  /** `onPaper` 종이톤 티켓 위 · `onDark` 다크 티켓 위. */
  tone?: StampTone
}

const { label, tone = 'onPaper' } = defineProps<Props>()

const TONE_CLASS: Record<StampTone, string> = {
  onPaper: 'border-on-paper/75 text-on-paper',
  onDark: 'border-ink/75 text-ink',
}
</script>

<template>
  <span
    aria-hidden="true"
    class="flex size-16 -rotate-8 items-center justify-center rounded-pill border-2 px-2 text-center font-display text-caption uppercase opacity-70"
    :class="TONE_CLASS[tone]"
  >
    <!--
      문구 길이가 로케일마다 크게 다르다. `Ended`(5자)가 `Đã kết thúc`(11자)이 되면
      12px 그대로는 원을 뚫고 나간다 — vi에서 실제로 그랬다.

      🔴 `v-fit-text`는 사각 경계로만 재므로 원의 곡선까지는 모른다(#399에서 배운 것).
      좌우 `px-2`로 곡선 몫을 미리 비워 두고, 남은 폭에 글자를 맞춘다. 하한에서는
      줄바꿈으로 넘긴다.
    -->
    <span
      v-fit-text.wrap
      class="min-w-0"
    >
      {{ label }}
    </span>
  </span>
</template>
