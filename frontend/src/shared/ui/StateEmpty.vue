<script setup lang="ts">
import { useI18n } from 'vue-i18n'

interface Props {
  title?: string
  description?: string
  /** 지정하면 행동 버튼을 보여주고 `action`을 emit한다. */
  actionLabel?: string
}

const { title = undefined, description = undefined, actionLabel = undefined } = defineProps<Props>()

const emit = defineEmits<{ action: [] }>()

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col items-center gap-2 py-12 text-center">
    <p class="text-title text-ink">{{ title ?? t('state.empty.title') }}</p>
    <p class="text-body-sm text-ink-3">{{ description ?? t('state.empty.description') }}</p>
    <button
      v-if="actionLabel !== undefined"
      type="button"
      class="mt-4 h-12 rounded-sm border border-hairline-strong px-6 text-title-sm text-ink"
      @click="emit('action')"
    >
      {{ actionLabel }}
    </button>
  </div>
</template>
