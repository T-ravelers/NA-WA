<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
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

/*
 * 이번 방문에 "새로 온 것"이 무엇이었는지 붙잡아 둔다.
 *
 * 들어오자마자 전부 읽음으로 바꾸기 때문에, 서버가 주는 읽음 여부를 그대로 그리면 점이
 * 찍히자마자 지워진다. 사용자는 무엇이 새로 왔는지 볼 기회를 잃는다. 그래서 처음 받아 온
 * 목록의 안 읽음 상태를 그대로 붙들고, 나가서 다시 들어오면 그때는 읽은 것으로 보인다.
 */
const unreadWhenOpened = ref<Set<string> | null>(null)

watch(
  notifications,
  (list) => {
    if (unreadWhenOpened.value !== null || list.length === 0) return
    unreadWhenOpened.value = new Set(
      list.filter((notification) => !notification.isRead).map((notification) => notification.id),
    )
  },
  { immediate: true },
)

function isUnread(notification: AppNotification): boolean {
  return unreadWhenOpened.value === null
    ? !notification.isRead
    : unreadWhenOpened.value.has(notification.id)
}

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
          <!--
            버튼에 aria-label을 걸지 않는다. 걸면 그 말이 버튼의 이름을 통째로 덮어써서,
            화면을 못 보는 사람은 알림마다 똑같은 한 마디만 듣고 누가 무엇을 요청했는지는
            영영 듣지 못한다. 안에 있는 글이 그대로 버튼의 이름이 되게 둔다.
          -->
          <button
            type="button"
            class="w-full text-left"
            @click="openSettlement(notification)"
          >
            <!--
              안 읽은 알림에만 점을 붙인다. 점은 장식이라 접근성 트리에서 감추고, 대신
              눈에 보이지 않는 "Unread"를 같이 둔다 — 읽음 여부를 점 하나로만 말하면
              화면을 못 보는 사람에게는 아무 말도 하지 않은 것과 같다.
            -->
            <div class="flex items-start gap-3">
              <span
                v-if="isUnread(notification)"
                aria-hidden="true"
                class="mt-2 size-2 shrink-0 rounded-pill bg-ink"
              ></span>
              <div class="min-w-0 flex-1">
                <p class="text-body-sm text-ink">
                  <span
                    v-if="isUnread(notification)"
                    class="sr-only"
                    >{{ t('notification.unread') }}</span
                  >
                  {{ messageFor(notification) }}
                </p>
                <p class="mt-1 text-caption text-ink-3">{{ timeFor(notification) }}</p>
              </div>
            </div>
          </button>
        </AppCard>
      </li>
    </ul>
  </main>
</template>
