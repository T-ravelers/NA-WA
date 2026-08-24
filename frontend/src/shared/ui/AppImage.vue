<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import ImagePlaceholder from './ImagePlaceholder.vue'

interface Props {
  src?: string | null
  alt?: string
  placeholderLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  src: null,
  alt: '',
  placeholderLabel: undefined,
})

defineOptions({ inheritAttrs: false })

const failed = ref(false)
const canRenderImage = computed(() => Boolean(props.src) && !failed.value)
const fallbackLabel = computed(() => props.placeholderLabel ?? (props.alt || undefined))

watch(
  () => props.src,
  () => {
    failed.value = false
  },
)

function showFallback(): void {
  failed.value = true
}
</script>

<template>
  <img
    v-if="canRenderImage"
    v-bind="$attrs"
    :src="src ?? undefined"
    :alt="alt"
    @error="showFallback"
  />
  <slot v-else>
    <ImagePlaceholder
      v-bind="$attrs"
      :label="fallbackLabel"
    />
  </slot>
</template>
