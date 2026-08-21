<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

import AppButton from '@/shared/ui/AppButton.vue'

interface Props {
  title: string
  description: string
  confirmLabel: string
  cancelLabel?: string
  pending?: boolean
  destructive?: boolean
  singleAction?: boolean
}

const {
  title,
  description,
  confirmLabel,
  cancelLabel = undefined,
  pending = false,
  destructive = false,
  singleAction = false,
} = defineProps<Props>()

const emit = defineEmits<{ confirm: []; cancel: [] }>()

function cancel(): void {
  if (!pending) emit('cancel')
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') cancel()
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <div
    class="fixed inset-0 z-40 flex items-center justify-center bg-scrim/60 px-screen"
    @click.self="cancel"
  >
    <section
      role="dialog"
      aria-modal="true"
      :aria-labelledby="`${$attrs.id ?? 'journey-dialog'}-title`"
      :aria-describedby="`${$attrs.id ?? 'journey-dialog'}-description`"
      class="flex w-full max-w-[340px] flex-col gap-2.5 rounded-lg bg-surface-2 px-5 pb-5 pt-6 shadow-sheet"
    >
      <h2
        :id="`${$attrs.id ?? 'journey-dialog'}-title`"
        class="text-title text-ink"
      >
        {{ title }}
      </h2>
      <p
        :id="`${$attrs.id ?? 'journey-dialog'}-description`"
        class="text-body-sm leading-relaxed text-ink-3"
      >
        {{ description }}
      </p>
      <slot />
      <div class="mt-2.5 flex gap-2">
        <AppButton
          v-if="!singleAction"
          variant="secondary"
          class="min-w-0 flex-1"
          :disabled="pending"
          @click="cancel"
        >
          {{ cancelLabel }}
        </AppButton>
        <button
          v-if="destructive"
          type="button"
          :disabled="pending"
          :aria-busy="pending"
          class="flex h-12 flex-1 items-center justify-center rounded-sm bg-danger px-4 text-title-sm text-ink transition-transform active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40"
          @click="emit('confirm')"
        >
          {{ confirmLabel }}
        </button>
        <AppButton
          v-else
          class="min-w-0 flex-1"
          :loading="pending"
          @click="emit('confirm')"
        >
          {{ confirmLabel }}
        </AppButton>
      </div>
    </section>
  </div>
</template>
