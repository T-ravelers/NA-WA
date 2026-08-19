<script setup lang="ts">
import { computed } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import JourneyCreateForm from '../components/JourneyCreateForm.vue'
import { createJourney, type JourneyCreateInput } from '../api/journeyApi'
import { journeyErrorMessageKey } from '../model/journeyErrors'
import { journeyKeys } from '../model/journeyQueries'

const i18n = useI18n()
const { t } = i18n
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const hasMessage = (key: string): boolean => i18n.te(key)

/**
 * 다른 화면이 여정이 없어 이 화면으로 보낸 경우, 생성 후 그 화면으로 돌아간다.
 *
 * `returnRouteName`은 임의의 route 이름 문자열이라 이 feature가 호출자를 알 필요가
 * 없다. 나머지 query(예: appointment-create의 itemId·itemType)는 그대로 들고
 * 돌아가고, 새로 만든 tripId만 더한다.
 */
function returnRouteName(): string | null {
  const value = route.query.returnRouteName
  return typeof value === 'string' && value !== '' ? value : null
}

const createMutation = useMutation({
  mutationFn: createJourney,
  onSuccess: async (journey) => {
    queryClient.setQueryData(journeyKeys.detail(journey.tripId), journey)

    const returnTo = returnRouteName()
    if (returnTo !== null) {
      const restQuery = { ...route.query }
      delete restQuery.returnRouteName
      await router.push({
        name: returnTo,
        query: { ...restQuery, tripId: String(journey.tripId) },
      })
      return
    }

    await router.push({ name: 'journey-detail', params: { tripId: journey.tripId } })
  },
})

const errorMessage = computed(() =>
  createMutation.error.value === null
    ? undefined
    : t(journeyErrorMessageKey(createMutation.error.value, hasMessage)),
)

function submit(input: JourneyCreateInput): void {
  if (!createMutation.isPending.value) {
    createMutation.mutate(input)
  }
}
</script>

<template>
  <main class="flex w-full flex-col gap-6 px-screen py-8">
    <h1 class="font-display text-screen-title uppercase text-ink-display">
      {{ t('journey.create.title') }}
    </h1>
    <JourneyCreateForm
      :pending="createMutation.isPending.value"
      :error-message="errorMessage"
      @submit="submit"
    />
  </main>
</template>
