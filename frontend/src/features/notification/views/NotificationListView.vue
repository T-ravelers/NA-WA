<script setup lang="ts">
import { IconX } from '@tabler/icons-vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { formatServerDateTime } from '@/shared/lib/datetime'
import { formatCurrency } from '@/shared/lib/money'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import {
  notificationMessageKey,
  settlementReturnQuery,
  settlementSideFor,
  type AppNotification,
} from '../model/notification'
import {
  useDeleteAllNotifications,
  useDeleteNotification,
  useNotifications,
  useReadAllNotifications,
  useReadNotification,
} from '../model/notificationQueries'

const i18n = useI18n()
const { t, locale } = i18n
const router = useRouter()

/*
 * 받아 온 쪽을 이어 붙인다.
 *
 * "더 보기"는 커서를 바꿔 다음 쪽을 부르고, 그 응답이 도착하면 여기에 덧붙는다. 앞 쪽을
 * 버리지 않는 것은 사용자가 이미 읽고 있던 자리를 잃지 않게 하기 위해서다.
 */
const cursor = ref<string | undefined>(undefined)
const loaded = ref<AppNotification[]>([])

const { notifications, nextCursor, isPending, isFetching, isError, error } =
  useNotifications(cursor)

watch(
  notifications,
  (page) => {
    if (cursor.value === undefined) {
      loaded.value = page
      return
    }
    const known = new Set(loaded.value.map((notification) => notification.id))
    loaded.value = [...loaded.value, ...page.filter((notification) => !known.has(notification.id))]
  },
  { immediate: true },
)

const readOne = useReadNotification()
const readAll = useReadAllNotifications()
const deleteOne = useDeleteNotification()
const deleteAll = useDeleteAllNotifications()

const hasUnread = computed(() => loaded.value.some((notification) => !notification.isRead))
/* 커서를 되돌리면 첫 쪽부터 다시 받는다. 지우고 난 뒤 남은 것을 다시 세는 데도 쓴다. */
function resetToFirstPage(): void {
  cursor.value = undefined
  loaded.value = []
}

function loadMore(): void {
  if (nextCursor.value === null || isFetching.value) return
  cursor.value = nextCursor.value
}

function dismiss(notification: AppNotification): void {
  loaded.value = loaded.value.filter((item) => item.id !== notification.id)
  deleteOne.mutate(notification.id, { onError: () => resetToFirstPage() })
}

function markAllRead(): void {
  loaded.value = loaded.value.map((notification) => ({ ...notification, isRead: true }))
  readAll.mutate(undefined, { onError: () => resetToFirstPage() })
}

function dismissAll(): void {
  loaded.value = []
  deleteAll.mutate(undefined, { onError: () => resetToFirstPage() })
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

/*
 * 알림을 눌러 정산 상세로 간다.
 *
 * 이동하기 전에 이 알림만 읽음으로 바꾼다. 화면에 들어온 것만으로 전부 읽음 처리하지
 * 않으므로, 무엇을 실제로 봤는지가 그대로 남는다.
 *
 * 주소에 어디서 왔는지를 함께 남긴다. 이 표시가 없으면 정산 상세에서 뒤로 갔을 때 정산
 * 홈으로 떨어져, 벨을 눌러 들어온 사용자가 지갑에서 두 화면이나 떨어진 곳에 서게 된다.
 */
function openSettlement(notification: AppNotification): void {
  if (!notification.isRead) {
    readOne.mutate(notification.id)
    loaded.value = loaded.value.map((item) =>
      item.id === notification.id ? { ...item, isRead: true } : item,
    )
  }

  void router.push({
    name: 'settlement-detail',
    params: { settlementId: notification.settlementId },
    query: settlementReturnQuery(settlementSideFor(notification.kind)),
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

    <!--
      두 가지 일괄 동작은 헤더가 아니라 그 아래 줄에 둔다. 390px 폭에서 제목과 나란히
      세우면 셋 다 잘린다. 할 일이 없을 때는 아예 보이지 않게 해서 줄 자체가 사라진다.
    -->
    <div
      v-if="loaded.length > 0"
      class="mt-3 flex items-center justify-end gap-1"
    >
      <AppButton
        v-if="hasUnread"
        compact
        variant="tertiary"
        data-testid="notification-mark-all-read"
        @click="markAllRead"
      >
        {{ t('notification.markAllRead') }}
      </AppButton>
      <AppButton
        compact
        variant="tertiary"
        data-testid="notification-dismiss-all"
        @click="dismissAll"
      >
        {{ t('notification.dismissAll') }}
      </AppButton>
    </div>

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
      v-else-if="loaded.length === 0"
      class="my-auto"
      :title="t('notification.empty.title')"
      :description="t('notification.empty.description')"
    />

    <template v-else>
      <ul class="mt-5 flex flex-col gap-2">
        <li
          v-for="notification in loaded"
          :key="notification.id"
        >
          <AppCard>
            <!--
              내용 버튼과 X는 형제로 둔다. 카드 전체를 버튼으로 감싸고 그 안에 X를 넣으면
              버튼 안에 버튼이 들어가서, 화면을 못 보는 사람에게는 둘 중 하나가 아예
              닿지 않고 브라우저마다 눌리는 대상도 달라진다.
            -->
            <div class="flex items-start gap-2">
              <!--
                내용 버튼에는 aria-label을 걸지 않는다. 걸면 그 말이 버튼의 이름을 통째로
                덮어써서, 화면을 못 보는 사람은 알림마다 똑같은 한 마디만 듣고 누가 무엇을
                요청했는지는 영영 듣지 못한다. 안에 있는 글이 그대로 이름이 되게 둔다.
              -->
              <button
                type="button"
                class="min-w-0 flex-1 text-left"
                @click="openSettlement(notification)"
              >
                <!--
                  안 읽은 알림에만 점을 붙인다. 점은 장식이라 접근성 트리에서 감추고, 대신
                  눈에 보이지 않는 "Unread"를 같이 둔다 — 읽음 여부를 점 하나로만 말하면
                  화면을 못 보는 사람에게는 아무 말도 하지 않은 것과 같다.
                -->
                <div class="flex items-start gap-3">
                  <span
                    v-if="!notification.isRead"
                    aria-hidden="true"
                    class="mt-2 size-2 shrink-0 rounded-pill bg-ink"
                  ></span>
                  <div class="min-w-0 flex-1">
                    <p class="text-body-sm text-ink">
                      <span
                        v-if="!notification.isRead"
                        class="sr-only"
                        >{{ t('notification.unread') }}</span
                      >
                      {{ messageFor(notification) }}
                    </p>
                    <p class="mt-1 text-caption text-ink-3">{{ timeFor(notification) }}</p>
                  </div>
                </div>
              </button>

              <!--
                X는 아이콘뿐이라 읽어 줄 이름이 없다. 알림마다 같은 "Dismiss"만 들리면 무엇을
                지우는 버튼인지 알 수 없으므로, 그 알림의 문장을 이름에 함께 싣는다.
              -->
              <IconOrb
                :label="t('notification.dismissOne', { message: messageFor(notification) })"
                data-testid="notification-dismiss"
                @click="dismiss(notification)"
              >
                <IconX class="size-4" />
              </IconOrb>
            </div>
          </AppCard>
        </li>
      </ul>

      <AppButton
        v-if="nextCursor !== null"
        class="mt-4"
        block
        variant="secondary"
        :disabled="isFetching"
        data-testid="notification-load-more"
        @click="loadMore"
      >
        {{ isFetching ? t('notification.loadingMore') : t('notification.loadMore') }}
      </AppButton>
    </template>
  </main>
</template>
