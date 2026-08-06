<script setup lang="ts">
import { IconPhoto } from '@tabler/icons-vue'

/**
 * 이미지가 없거나 불러오지 못했을 때 자리를 채우는 면.
 *
 * 수집 데이터의 결측이 커서(이벤트 썸네일 약 33%, 장소 약 44%) 예외가 아니라 기본 상태에
 * 가깝다. 빈 사각형을 두면 목록 절반이 구멍처럼 보이므로 면과 아이콘으로 채운다.
 *
 * **이미지 자산을 쓰지 않는다.** 전용 일러스트가 나오면 이 파일 하나만 교체하면 되도록
 * 여기에 가둔다. 카드마다 흩어 두지 않는다.
 */
interface Props {
  /** 컨테이너를 채운다. 비율과 라운드는 감싸는 쪽에서 정한다. */
  label?: string
}

const { label = undefined } = defineProps<Props>()
</script>

<template>
  <div
    class="flex size-full items-center justify-center bg-surface-2"
    :role="label === undefined ? 'presentation' : 'img'"
    :aria-label="label"
  >
    <IconPhoto
      :size="28"
      :stroke-width="1.5"
      class="text-icon-muted"
      aria-hidden="true"
    />
  </div>
</template>
