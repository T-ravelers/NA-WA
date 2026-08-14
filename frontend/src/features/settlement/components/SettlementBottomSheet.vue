<script setup lang="ts">
import { onBeforeUnmount, onMounted, useTemplateRef } from 'vue'

interface Props {
  label: string
}
defineProps<Props>()
const emit = defineEmits<{ close: [] }>()
const dialog = useTemplateRef('dialog')
let previousFocus: HTMLElement | null = null

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => {
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  window.addEventListener('keydown', handleKeydown)
  dialog.value
    ?.querySelector<HTMLElement>('button:not([disabled]), input:not([disabled]), [tabindex="0"]')
    ?.focus()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  previousFocus?.focus()
})
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-end justify-center bg-scrim/70"
    role="presentation"
    @click.self="emit('close')"
  >
    <section
      ref="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="label"
      class="max-h-[88dvh] w-full max-w-[390px] overflow-y-auto rounded-t-card bg-canvas px-screen pt-3 pb-8 shadow-sheet"
    >
      <div class="mx-auto h-1 w-10 rounded-pill bg-hairline-strong" />
      <div class="mt-6"><slot /></div>
    </section>
  </div>
</template>
