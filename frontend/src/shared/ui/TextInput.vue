<script setup lang="ts">
import { computed, useId } from 'vue'

/**
 * 라벨·도움말·오류를 함께 묶은 텍스트 입력.
 *
 * 도메인에서 `<input>`을 직접 쓰지 않는다. 라벨 연결과 오류 안내가 빠지기 쉬운데,
 * 그러면 스크린 리더에서 무엇을 입력하는 칸인지 읽히지 않는다.
 *
 * 시안 실측: `surface-2` · h52 · 라운드 12 · focus 2px `ink` · error 2px `danger`.
 */
interface Props {
  modelValue: string
  /** 입력 위에 노출되는 라벨. 시각적으로 감출 수는 있어도 생략할 수 없다. */
  label: string
  placeholder?: string
  /**
   * 오류 문구. 지정하면 테두리를 `danger`로 바꾸고 하단에 안내를 붙인다.
   * 오류를 색으로만 말하지 않기 위해 문구를 필수로 받는다.
   */
  error?: string
  /** 평상시 하단 도움말. `error`가 있으면 그쪽이 우선한다. */
  helper?: string
  type?: 'text' | 'email' | 'search'
  /** 라벨을 스크린 리더 전용으로 감춘다. 검색창처럼 맥락이 분명한 곳에만 쓴다. */
  labelHidden?: boolean
}

const {
  modelValue,
  label,
  placeholder = undefined,
  error = undefined,
  helper = undefined,
  type = 'text',
  labelHidden = false,
} = defineProps<Props>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const inputId = useId()
const messageId = `${inputId}-message`

const hasError = computed(() => error !== undefined && error !== '')
const message = computed(() => (hasError.value ? error : helper))

function handleInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <label
      :for="inputId"
      class="text-caption text-ink-2"
      :class="labelHidden ? 'sr-only' : ''"
    >
      {{ label }}
    </label>
    <input
      :id="inputId"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :aria-invalid="hasError"
      :aria-describedby="message === undefined ? undefined : messageId"
      class="h-13 w-full rounded-sm bg-surface-2 px-4 text-body text-ink outline-none placeholder:text-ink-3"
      :class="
        hasError ? 'border-2 border-danger' : 'border-2 border-transparent focus-visible:border-ink'
      "
      @input="handleInput"
    />
    <p
      v-if="message !== undefined"
      :id="messageId"
      class="text-caption"
      :class="hasError ? 'text-danger' : 'text-ink-3'"
    >
      {{ message }}
    </p>
  </div>
</template>
