<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { formatServerDateTime } from '@/shared/lib/datetime'
import { formatCurrency } from '@/shared/lib/money'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import {
  notificationMessageKey,
  settlementSideFor,
  type AppNotification,
} from '../model/notification'
import { useNotifications, useReadAllNotifications } from '../model/notificationQueries'

const i18n = useI18n()
const { t, locale } = i18n
const router = useRouter()

const { notifications, isPending, isError, error } = useNotifications()
const readAll = useReadAllNotifications()

/*
 * 화면에 들어온 것 자체가 "봤다"는 뜻이라 여기서 한 번 읽음으로 바꾼다.
 *
 * 실패해도 화면은 그대로 둔다. 목록은 이미 보이고 있고, 배지가 잠시 남는 것은 알림을
 * 못 보는 것보다 훨씬 가벼운 문제다. 다음 진입에서 다시 시도된다.
 */
onMounted(() => {
  readAll.mutate()
})

function messageFor(notification: AppNotification): string {
  return t(notificationMessageKey(notification.kind), {
    actor: notification.actorName,
    gathering: notification.gatheringName,
    amount: formatCurrency(notification.amount, locale.value, notification.currencyCode),
  })
}

function timeFor(notification: AppNotification): string {
  return formatServerDateTime(notification.createdAt, locale.value, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

function openSettlement(notification: AppNotification): void {
  const side = settlementSideFor(notification.kind)

  void router.push({
    name: 'settlement-detail',
    params: { settlementId: notification.settlementId },
    query: side === undefined ? undefined : { side },
  })
}

/** 실패 사유. 번역된 코드가 있을 때만 덧붙이고, 서버 message는 화면에 내지 않는다. */
function errorDescription(): string | undefined {
  const failure = error.value

  if (!(failure instanceof NormalizedApiError) || !i18n.te(failure.messageKey)) {
    return undefined
  }

  return t(failure.messageKey)
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col px-screen pt-6 pb-8">
    <header class="flex items-center gap-3">
      <AppButton
        compact
        variant="secondary"
        :aria-label="t('action.back')"
        @click="router.back()"
      >
        ‹
      </AppButton>
      <h1
        class="min-w-0 flex-1 truncate font-display text-section-header text-ink-display uppercase"
      >
        {{ t('notification.title') }}
      </h1>
    </header>

    <StateLoading
      v-if="isPending"
      class="mt-8"
    />

    <StateError
      v-else-if="isError"
      class="my-auto"
      :description="errorDescription()"
    />

    <StateEmpty
      v-else-if="notifications.length === 0"
      class="my-auto"
      :title="t('notification.empty.title')"
      :description="t('notification.empty.description')"
    />

    <ul
      v-else
      class="mt-5 flex flex-col gap-2"
    >
      <li
        v-for="notification in notifications"
        :key="notification.id"
      >
        <AppCard>
          <button
            type="button"
            class="w-full text-left"
            :aria-label="t('notification.openSplit')"
            @click="openSettlement(notification)"
          >
            <!--
              안 읽은 알림에만 점을 붙인다. 점은 장식이라 접근성 트리에서 감춘다 —
              읽음 여부는 색이나 점이 아니라 문장으로도 구분되어야 한다.
            -->
            <div class="flex items-start gap-3">
              <span
                v-if="!notification.isRead"
                aria-hidden="true"
                class="mt-2 size-2 shrink-0 rounded-pill bg-ink"
              ></span>
              <div class="min-w-0 flex-1">
                <p class="text-body-sm text-ink">{{ messageFor(notification) }}</p>
                <p class="mt-1 text-caption text-ink-3">{{ timeFor(notification) }}</p>
              </div>
            </div>
          </button>
        </AppCard>
      </li>
    </ul>
  </main>
</template>
