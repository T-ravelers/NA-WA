<script setup lang="ts">
import { IconArrowLeft } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import JourneyCreateForm from '../components/JourneyCreateForm.vue'
import { createJourney, type JourneyCreateInput } from '../api/journeyApi'
import { useJourneyExploreIntegration } from '../model/exploreIntegration'
import { journeyErrorMessageKey } from '../model/journeyErrors'
import { journeyKeys } from '../model/journeyQueries'

const i18n = useI18n()
const { t } = i18n
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const { consumeReturn } = useJourneyExploreIntegration()
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

// 뒤로 가기 목적지. 제출 후 복귀와 같은 규칙이다 — 다른 화면이 보낸 경우 그 화면으로
// 나머지 query(itemId·itemType 등)를 그대로 들고 돌아가고, 직접 들어온 경우는 여정
// 목록이다. 만든 것이 없으니 tripId는 더하지 않는다.
const backTarget = computed<RouteLocationRaw>(() => {
  const returnTo = returnRouteName()
  if (returnTo === null) return { name: 'journey-list' }
  const restQuery = { ...route.query }
  delete restQuery.returnRouteName
  return { name: returnTo, query: restQuery }
})

/**
 * 뒤로 가면 이 화면의 히스토리 엔트리를 소비한다. 목적지를 `push`하면 이 화면이
 * 히스토리에 남아, 돌아간 약속 생성 화면에서 시트를 닫을 때 목록이 아니라 이미
 * 제출한 이 폼으로 다시 튄다.
 *
 * 다른 화면이 보낸 경우에는 되감지 않고 그 화면으로 `replace` 한다 — 그쪽이 자기
 * 자리를 이 화면에 내주고 보냈기 때문에(`replace`로 진입) 되감으면 흐름 이전 화면까지
 * 빠져 버린다. 자리를 돌려주면 제출 후 복귀와 같은 자리가 되고, 같은 화면이 히스토리에
 * 두 번 쌓이지 않는다.
 *
 * 직접 들어온 경우는 왔던 길을 되감고, 되감을 것이 없을 때(딥링크·PWA 재진입)만
 * 여정 목록으로 보낸다.
 */
function goBack(): void {
  if (returnRouteName() !== null) {
    void router.replace(backTarget.value)
    return
  }
  if (window.history.length > 1) {
    void router.back()
    return
  }
  void router.push(backTarget.value)
}

const createMutation = useMutation({
  mutationFn: createJourney,
  onSuccess: async (journey) => {
    queryClient.setQueryData(journeyKeys.detail(journey.tripId), journey)
    // 여정 목록 쿼리(다른 feature의 journeyKeys.list()도 같은 'journeys' 루트를
    // 쓴다)를 무효화한다. 안 하면 30초 staleTime 안에 목록을 다시 조회할 때
    // (예: 약속 생성의 여정 선택 시트) 방금 만든 여정이 안 보인다.
    await queryClient.invalidateQueries({ queryKey: journeyKeys.all })

    /*
     * route param을 나르는 복귀다. 새 여정 id는 query로 실어 보낸다 — Discover 상세의
     * `activeJourneyId`가 query를 먼저 보므로 그대로 담기 대상이 된다.
     *
     * `openJourneySelect`는 하던 일을 그대로 이어 담기 시트를 다시 열라는 표시다.
     */
    if (isExploreReturn()) {
      const destination = consumeReturn()
      if (destination !== null) {
        await router.push({
          name: destination.name,
          params: destination.params,
          query: { journeyId: String(journey.tripId), openJourneySelect: '1' },
        })
        return
      }
    }

    const returnTo = returnRouteName()
    if (returnTo !== null) {
      const restQuery = { ...route.query }
      delete restQuery.returnRouteName
      // push가 아니라 replace다. push하면 이 화면이 히스토리에 남아, 돌아간
      // 화면에서 흐름을 포기할 때(되감기) 목록이 아니라 이미 제출한 이 폼으로
      // 다시 튄다. 제출이 끝난 화면은 되돌아올 이유가 없으니 자리를 내준다.
      await router.replace({
        name: returnTo,
        query: { ...restQuery, tripId: String(journey.tripId) },
      })
      return
    }

    // 여기도 replace다. push하면 제출이 끝난 이 폼이 히스토리에 남아, 상세에서 뒤로
    // 갈 때 방금 제출한 폼이 다시 뜬다. 위의 호출자 복귀와 같은 규칙이다.
    await router.replace({ name: 'journey-detail', params: { tripId: journey.tripId } })
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
      <button
        type="button"
        :aria-label="t('action.back')"
        class="-ml-3 flex size-11 shrink-0 items-center justify-center text-ink"
        @click="goBack"
      >
        <IconArrowLeft
          :size="24"
          :stroke-width="1.75"
          aria-hidden="true"
        />
      </button>
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
