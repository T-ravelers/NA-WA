<script setup lang="ts">
import { IconArrowLeft } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

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

// 뒤로 가기도 제출 후 복귀와 같은 규칙을 쓴다. 다른 화면이 보낸 경우 그 화면으로
// 나머지 query(itemId·itemType 등)를 그대로 들고 돌아가고, 직접 들어온 경우는
// 여정 목록이다. 만든 것이 없으니 tripId는 더하지 않는다.
const backTarget = computed<RouteLocationRaw>(() => {
  const returnTo = returnRouteName()
  if (returnTo === null) return { name: 'journey-list' }
  const restQuery = { ...route.query }
  delete restQuery.returnRouteName
  return { name: returnTo, query: restQuery }
})

const createMutation = useMutation({
  mutationFn: createJourney,
  onSuccess: async (journey) => {
    queryClient.setQueryData(journeyKeys.detail(journey.tripId), journey)
    // 여정 목록 쿼리(다른 feature의 journeyKeys.list()도 같은 'journeys' 루트를
    // 쓴다)를 무효화한다. 안 하면 30초 staleTime 안에 목록을 다시 조회할 때
    // (예: 약속 생성의 여정 선택 시트) 방금 만든 여정이 안 보인다.
    await queryClient.invalidateQueries({ queryKey: journeyKeys.all })

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
    <header class="flex items-center gap-0.5">
      <RouterLink
        :to="backTarget"
        :aria-label="t('action.back')"
        class="-ml-3 flex size-11 shrink-0 items-center justify-center text-ink"
      >
        <IconArrowLeft
          :size="24"
          :stroke-width="1.75"
          aria-hidden="true"
        />
      </RouterLink>
      <h1 class="font-display text-screen-title uppercase text-ink-display">
        {{ t('journey.create.title') }}
      </h1>
    </header>
    <JourneyCreateForm
      :pending="createMutation.isPending.value"
      :error-message="errorMessage"
      @submit="submit"
    />
  </main>
</template>
