<script setup lang="ts">
import { computed } from 'vue'

import SegmentedControl from '@/shared/ui/SegmentedControl.vue'

interface Props {
  modelValue: 'events' | 'places'
  eventsLabel: string
  placesLabel: string
  label: string
}

const props = defineProps<Props>()

const emit = defineEmits<{ 'update:modelValue': [value: Props['modelValue']] }>()

const options = computed(() => [
  { value: 'events', label: props.eventsLabel },
  { value: 'places', label: props.placesLabel },
])
</script>

<template>
  <SegmentedControl
    :model-value="modelValue"
    :options="options"
    :label="label"
    @update:model-value="emit('update:modelValue', $event as Props['modelValue'])"
  />
</template>
