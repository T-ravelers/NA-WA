<script setup lang="ts">
import { IconCheck } from '@tabler/icons-vue'

import { LOCALE_LABEL, SUPPORTED_LOCALES, type AppLocale } from '@/shared/i18n/locales'

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

      <h2 class="mb-1 font-display text-section-header text-ink-display uppercase">{{ title }}</h2>

      <button
        v-for="locale in SUPPORTED_LOCALES"
        :key="locale"
        type="button"
        role="radio"
        :aria-checked="locale === modelValue"
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

      <p
        v-if="hint !== undefined"
        class="mt-1.5 text-micro leading-relaxed font-normal text-ink-3"
      >
        {{ hint }}
      </p>
    </div>
  </div>
</template>
