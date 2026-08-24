<script setup lang="ts">
/**
 * 화면 제목 헤더. 조형이 여기 한 곳에 있다.
 *
 * 뷰마다 `<header>`를 직접 쓰던 때 패턴이 여섯 종으로 갈렸다(`flex items-center gap-3` 11 ·
 * `border-b border-hairline py-4` 5 · `justify-between gap-4` 2 · `gap-0.5` 2 · `flex-col gap-2` 1).
 * 뒤로가기도 `‹` 문자, 맨손 `<button>`, `RouterLink`, `IconOrb`가 섞여 터치 타깃이 화면마다 달랐다.
 *
 * 규격은 `frontend/docs/DEVELOPMENT_CONVENTION.md`의 「화면 조형 맞추기」가 정본이다.
 *
 * 덮지 않는 헤더가 둘 있다. `SettlementFlowHeader`(제목 아래 단계 눈금)와
 * `EventDetailView`·`PlaceDetailView`의 `sticky top-0` 반투명 띠(제목이 없고 폭을 꽉 채운다).
 */
import { vFitText } from '@/shared/lib/fitText'
import IconOrb from '@/shared/ui/IconOrb.vue'
import { IconArrowLeft } from '@tabler/icons-vue'

type ScreenHeaderVariant = 'back' | 'root'

interface Props {
  /** `back` 뒤로가기 있음 · `root` 뒤로갈 곳 없는 화면. */
  variant: ScreenHeaderVariant
  /** 화면 제목. */
  title: string
  /**
   * 뒤로가기 버튼의 접근 가능한 이름. `back`에서만 쓴다.
   *
   * 아이콘만 있는 버튼이라 생략할 수 없다.
   */
  backLabel?: string
}

const { variant, title, backLabel = '' } = defineProps<Props>()

const emit = defineEmits<{ back: [] }>()

const slots = defineSlots<{
  /** 제목 오른쪽 액션. `IconOrb` 한 개를 넣는다. */
  action?: () => unknown
  /** 제목 아래 보조 문장. 화면을 한 줄로 설명하는 곳에만 쓴다. */
  description?: () => unknown
}>()
</script>

<template>
  <!--
    `border-b`로 본문과 나누지 않는다. 컨테이너의 `gap-8`이 간격을 준다.

    보조 문장이 있을 때만 세로로 쌓는다. 제목과 문장 사이 8px은 컨테이너의 32px과 달라야
    둘이 한 덩어리로 읽힌다. 문장이 없으면 자식이 제목 행 하나뿐이라 `block`으로 둔다.
  -->
  <header :class="slots.description ? 'flex flex-col gap-2' : undefined">
    <div class="flex items-center gap-3">
      <!--
        `plain`은 배경이 없어 아이콘 글리프가 `px-screen` 안쪽으로 들어가 보인다.
        왼쪽으로 당겨 제목 세로선과 광학적으로 맞춘다.

        `data-testid`는 이 컴포넌트가 소유한다. 뷰마다 `settlement-back`·`notification-back`
        처럼 다른 이름을 붙이던 것을 하나로 모았다. 스크린샷 러너가 접근 이름으로 찾으면
        번역된 로케일에서 못 찾으므로(#489 실측) 구조에 붙은 훅이 필요하다.
      -->
      <IconOrb
        v-if="variant === 'back'"
        data-testid="screen-back"
        :label="backLabel"
        size="md"
        variant="plain"
        class="-ml-2.5"
        @click="emit('back')"
      >
        <IconArrowLeft
          :size="24"
          aria-hidden="true"
        />
      </IconOrb>

      <!--
        `--text-screen-title--font-weight`가 이미 700이라 `font-bold`를 붙이지 않는다.
        좁은 폭에서는 글자를 줄이고(#361), 50%에 닿으면 말줄임으로 넘긴다.
      -->
      <h1
        v-fit-text
        class="min-w-0 flex-1 truncate font-display text-screen-title uppercase text-ink-display"
      >
        {{ title }}
      </h1>

      <slot name="action" />
    </div>

    <p
      v-if="slots.description"
      class="text-body-sm text-ink-3"
    >
      <slot name="description" />
    </p>
  </header>
</template>
