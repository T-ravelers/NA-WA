<script setup lang="ts">
import { computed, useId } from 'vue'

/**
 * 금액 입력.
 *
 * 통화기호를 앞에 두고 숫자는 우측 정렬한다. 예산·정산 금액이 자릿수가 흔들리면
 * 읽기 어려워 `tabular-nums`를 유지한다(전역 base에서 이미 켜져 있다).
 *
 * 값은 문자열이 아니라 숫자로 다룬다. 화면마다 파싱 규칙을 다시 만들면 통화·구분자
 * 처리가 갈라진다. 비어 있는 입력은 `0`이 아니라 `null`이다 — "0원"과 "아직 입력하지
 * 않음"은 정산에서 다른 뜻이다.
 */
interface Props {
  modelValue: number | null
  label: string
  /** 통화기호. 표시 전용이며 값에 포함되지 않는다. */
  currencySymbol?: string
  error?: string
  helper?: string
  placeholder?: string
}

const {
  modelValue,
  label,
  currencySymbol = '₩',
  error = undefined,
  helper = undefined,
  placeholder = undefined,
} = defineProps<Props>()

const emit = defineEmits<{ 'update:modelValue': [value: number | null] }>()

const inputId = useId()
const messageId = `${inputId}-message`

const hasError = computed(() => error !== undefined && error !== '')
const message = computed(() => (hasError.value ? error : helper))

/** 화면에는 천 단위 구분자를 넣어 보여준다. */
const displayValue = computed(() =>
  modelValue === null ? '' : new Intl.NumberFormat('en-US').format(modelValue),
)

function handleInput(event: Event): void {
  const input = event.target as HTMLInputElement
  const digits = input.value.replace(/\D/g, '')

  if (digits === '') {
    emit('update:modelValue', null)
    // 사용자가 지운 그대로 두지 않으면 커서가 튄다.
    input.value = ''
    return
  }

  emit('update:modelValue', Number(digits))
}
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <label
      :for="inputId"
      class="text-caption text-ink-2"
    >
      {{ label }}
    </label>
    <div
      class="flex h-14 items-center gap-2 rounded-sm bg-surface-2 px-4"
      :class="hasError ? 'border-2 border-danger' : 'border-2 border-transparent'"
    >
      <span
        aria-hidden="true"
        class="shrink-0 text-data-lg text-ink-3"
      >
        {{ currencySymbol }}
      </span>
      <input
        :id="inputId"
        type="text"
        inputmode="numeric"
        :value="displayValue"
        :placeholder="placeholder"
        :aria-invalid="hasError"
        :aria-describedby="message === undefined ? undefined : messageId"
        class="min-w-0 flex-1 bg-transparent text-right text-data-lg text-ink outline-none placeholder:text-ink-3"
        @input="handleInput"
      />
    </div>
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
