<script setup lang="ts">
import { useI18n } from 'vue-i18n'

interface Props {
  title?: string
  description?: string
  actionLabel?: string
}

const { title = undefined, description = undefined, actionLabel = undefined } = defineProps<Props>()

const emit = defineEmits<{ retry: [] }>()

const { t } = useI18n()
</script>

<template>
  <div
    class="flex flex-col items-center gap-2 py-12 text-center"
    role="alert"
  >
    <p class="text-title text-ink">{{ title ?? t('state.error.title') }}</p>
    <p class="text-body-sm text-ink-3">{{ description ?? t('state.error.description') }}</p>
    <button
      type="button"
      class="mt-4 h-12 rounded-sm border border-hairline-strong px-6 text-title-sm text-ink"
      @click="emit('retry')"
    >
      {{ actionLabel ?? t('action.retry') }}
    </button>
  </div>
</template>
