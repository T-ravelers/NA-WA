<script setup lang="ts">
import { IconCheck } from '@tabler/icons-vue'
import { useId } from 'vue'

import { LOCALE_LABEL, SUPPORTED_LOCALES, type AppLocale } from '@/shared/i18n/locales'

import { useRovingRadioGroup } from './useRovingRadioGroup'

/**
 * 화면 언어를 고르는 바텀시트.
 *
 * 언어 이름 표는 `shared/i18n/locales.ts`의 `LOCALE_LABEL`이 정본이다. 로그인 화면·프로필도
 * 같은 표를 쓴다.
 *
 * 로그인 이전에도 열려야 하므로 서버 상태에 의존하지 않는다.
 */
interface Props {
  modelValue: AppLocale
  /** 시트 제목. 열려 있을 때 스크린 리더가 읽는다. */
  title: string
  /** 선택이 어떻게 반영되는지 알리는 보조 문구. */
  hint?: string
}

const { modelValue, title, hint = undefined } = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [locale: AppLocale]
  close: []
}>()

/** 라디오 그룹의 이름을 제목과 잇는다. 한 화면에 시트가 둘 이상 뜰 수 있어 id를 생성한다. */
const titleId = useId()

/*
 * 화살표 키 이동과 그룹당 탭 스톱 하나는 `useRovingRadioGroup`이 맡는다(#305/#433).
 * 그러지 않으면 선택지 네 개가 전부 탭 순서에 들어가, 시트를 지나 다음 요소로 가는 데
 * 탭을 세 번 더 눌러야 한다 — `SegmentedControl`과 같은 규약을 쓴다.
 *
 * 이 시트는 **고르는 즉시 적용하고 닫히므로** 화살표 한 번이 곧 선택이다(클릭과 같다).
 * 옮겨 다니다 나중에 확정하는 그룹이 아니라, 얻는 것은 탭 스톱이 하나로 줄어드는 쪽이다.
 */
const { onKeydown, tabindexFor } = useRovingRadioGroup(
  SUPPORTED_LOCALES,
  () => modelValue,
  (locale) => emit('update:modelValue', locale),
)
</script>

<template>
  <div class="fixed inset-0 z-30">
    <!-- 시트 밖을 누르면 닫힌다. 스크린 리더에는 시트만 노출한다. -->
    <button
      type="button"
      class="absolute inset-0 bg-scrim/60"
      tabindex="-1"
      aria-hidden="true"
      @click="emit('close')"
    />

    <!--
      조형은 V2 `언어설정`(`2297:2022`)이다 — 시트 바탕은 `canvas`, **행은 선택 여부와 무관하게
      모두 `surface-1` 카드**다. 고른 것은 면 색이 아니라 오른쪽 체크 원이 말한다.

      시안의 로케일 목록(English·한국어·日本語)은 조형 예시로 본다. 서비스 로케일은
      `SUPPORTED_LOCALES`가 정본이고 한국어는 거기 없다(2026-08-23 확정).
    -->
    <div
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      class="absolute inset-x-0 bottom-0 z-10 mx-auto flex w-full max-w-shell flex-col gap-2 rounded-t-lg bg-canvas px-screen pt-3 pb-8 shadow-sheet"
    >
      <span
        aria-hidden="true"
        class="mb-2 h-1 w-10 self-center rounded-pill bg-hairline-2"
      />

      <h2
        :id="titleId"
        class="mb-1 font-display text-section-header text-ink-display uppercase"
      >
        {{ title }}
      </h2>

      <!--
        `role="radio"`는 `radiogroup` 안에 있어야 한다. 그러지 않으면 스크린 리더가 「4개 중
        2번째」를 읽지 못한다. 행 배경을 통일해 선택 신호가 체크 원과 `aria-checked` 둘로
        줄었으므로 더 그렇다.
      -->
      <div
        role="radiogroup"
        :aria-labelledby="titleId"
        class="flex flex-col gap-2"
        @keydown="onKeydown"
      >
        <button
          v-for="locale in SUPPORTED_LOCALES"
          :key="locale"
          type="button"
          role="radio"
          :data-value="locale"
          :aria-checked="locale === modelValue"
          :tabindex="tabindexFor(locale)"
          class="flex min-h-14 items-center gap-3 rounded-sm bg-surface-1 px-3.5 text-left"
          @click="emit('update:modelValue', locale)"
        >
          <span class="flex flex-1 flex-col gap-px">
            <span class="text-title-sm text-ink-display">{{ LOCALE_LABEL[locale].native }}</span>
            <span class="text-caption text-ink-3">{{ LOCALE_LABEL[locale].english }}</span>
          </span>
          <span
            aria-hidden="true"
            class="flex size-6 items-center justify-center rounded-pill"
            :class="locale === modelValue ? 'bg-paper-fill' : 'border border-hairline-2'"
          >
            <IconCheck
              v-if="locale === modelValue"
              :size="16"
              :stroke-width="2.5"
              class="text-on-paper"
            />
          </span>
        </button>
      </div>

      <p
        v-if="hint !== undefined"
        class="mt-1.5 text-micro leading-relaxed font-normal text-ink-3"
      >
        {{ hint }}
      </p>
    </div>
  </div>
</template>
