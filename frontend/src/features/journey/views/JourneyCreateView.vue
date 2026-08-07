<script setup lang="ts">
import { computed } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import JourneyCreateForm from '../components/JourneyCreateForm.vue'
import { createJourney, type JourneyCreateInput } from '../api/journeyApi'
import { journeyErrorMessageKey } from '../model/journeyErrors'
import { journeyKeys } from '../model/journeyQueries'

const { t } = useI18n()
const router = useRouter()
const queryClient = useQueryClient()

const createMutation = useMutation({
  mutationFn: createJourney,
  onSuccess: async (journey) => {
    queryClient.setQueryData(journeyKeys.detail(journey.tripId), journey)
    await router.push({ name: 'journey-detail', params: { tripId: journey.tripId } })
  },
})

const errorMessage = computed(() =>
  createMutation.error.value === null
    ? undefined
    : t(journeyErrorMessageKey(createMutation.error.value)),
)

function submit(input: JourneyCreateInput): void {
  if (!createMutation.isPending.value) {
    createMutation.mutate(input)
  }
}
</script>

<template>
  <main class="mx-auto flex w-full max-w-screen-sm flex-col gap-6 px-5 py-8">
    <h1 class="text-display text-ink">{{ t('journey.create.title') }}</h1>
    <JourneyCreateForm
      :pending="createMutation.isPending.value"
      :error-message="errorMessage"
      @submit="submit"
    />
  </main>
</template>
