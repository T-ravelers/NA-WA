<script setup lang="ts">
/**
 * 작은 상태 표식.
 *
 * 여정 진행 상태, 정산 건수, 동행 인원처럼 카드 위에 얹히는 짧은 라벨을 담당한다.
 * 소비영역을 나타내야 한다면 이것이 아니라 `CategoryChip`을 쓴다.
 *
 * 상태를 색으로만 말하지 않는다. 모든 tone이 텍스트 라벨을 함께 요구하고, 점은
 * 라벨을 보조하는 장식으로만 붙는다.
 */
type BadgeTone =
  'ongoing' | 'scheduled' | 'info' | 'pending' | 'completed' | 'settlement' | 'onPaper' | 'neutral'

interface Props {
  tone?: BadgeTone
  /**
   * 앞에 상태 점을 붙인다. `ongoing`·`scheduled`·`pending`에서 쓴다.
   */
  dot?: boolean
}

const { tone = 'neutral', dot = false } = defineProps<Props>()

/*
 * `ongoing`·`scheduled`는 여정 대표 이미지 위에 얹힌다. 사진이 밝을 수 있어
 * 반투명 검정 면을 깔아 대비를 확보한다(시안 실측 rgba(23,23,23,0.72)).
 */
const TONE_CLASS: Record<BadgeTone, string> = {
  ongoing: 'bg-canvas/70 text-ink',
  scheduled: 'bg-canvas/70 text-ink',
  // 진행 중. `pending`(행동 필요, 앰버)·`completed`(끝남, 초록)와 나란히 놓여도 갈리도록 파랑.
  info: 'border border-info/40 bg-info/10 text-info',
  pending: 'border border-status-scheduled/40 bg-status-scheduled/10 text-status-scheduled',
  completed: 'border border-status-ongoing/40 bg-status-ongoing/10 text-status-ongoing',
  settlement: 'border border-settlement bg-transparent text-settlement',
  onPaper: 'border border-on-paper/30 bg-transparent text-on-paper/75',
  neutral: 'border border-hairline bg-transparent text-ink-2',
}

const DOT_CLASS: Record<BadgeTone, string> = {
  ongoing: 'bg-status-ongoing',
  scheduled: 'bg-status-scheduled',
  info: 'bg-info',
  pending: 'bg-status-scheduled',
  completed: 'bg-status-ongoing',
  settlement: 'bg-settlement',
  onPaper: 'bg-on-paper/50',
  neutral: 'bg-ink-3',
}
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center gap-1.5 rounded-pill px-2.5 py-1 text-micro"
    :class="TONE_CLASS[tone]"
  >
    <span
      v-if="dot"
      aria-hidden="true"
      class="size-1.5 shrink-0 rounded-pill"
      :class="DOT_CLASS[tone]"
    />
    <slot />
  </span>
</template>
