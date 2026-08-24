<script setup lang="ts">
import { IconX } from '@tabler/icons-vue'
import { computed } from 'vue'
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
import ScreenHeader from '@/shared/ui/ScreenHeader.vue'

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
 * 목록은 화면이 따로 들고 있지 않고 캐시에서 그대로 읽는다.
 *
 * 눌린 카드를 지우고 되돌리는 일은 전부 뮤테이션 쪽 캐시에서 일어난다. 여기에 사본을 하나
 * 더 두면 지우기가 실패해 캐시가 되돌아가도 그 사본은 그대로 남아, 눈앞의 목록과 실제가
 * 어긋난 채 굳는다.
 */
const { notifications, isPending, isError, error, hasNextPage, isFetchingNextPage, fetchNextPage } =
  useNotifications()

const readOne = useReadNotification()
const readAll = useReadAllNotifications()
const deleteOne = useDeleteNotification()
const deleteAll = useDeleteAllNotifications()

const hasUnread = computed(() => notifications.value.some((notification) => !notification.isRead))

/*
 * 첫 쪽을 기다리는 동안에만 화면을 로딩·오류로 덮는다.
 *
 * 보여 줄 것이 이미 있는데 덮어 버리면, "더 보기"를 누른 순간 읽고 있던 목록이 통째로
 * 사라졌다가 돌아온다. 다음 쪽이 실패했을 때도 마찬가지로 앞 쪽까지 잃는다.
 */
const isEmpty = computed(() => notifications.value.length === 0)

/*
 * 화면이 비었다고 알림이 없는 것은 아니다.
 *
 * X로 지우는 것은 지금 받아 온 쪽에서만 카드를 빼는 일이라, 다음 쪽은 서버에 그대로
 * 남아 있다. 그런데 "알림이 없습니다"로 덮어 버리면 그 안에 있던 더 보기 버튼까지 같이
 * 사라져서, 남은 알림에는 다시 닿을 길이 없어진다. 31건 중 앞의 30건을 하나씩 지우면
 * 31번째가 영영 보이지 않던 것이 이 경우다.
 *
 * 그래서 "더 받을 것이 없다"까지 확인됐을 때만 빈 상태로 넘어간다. 전체 오류도 같다 —
 * 이미 받아 둔 쪽이 있으면 그건 다음 쪽만 실패한 것이라 그 자리에서만 알려야 한다.
 */
const nothingLeft = computed(() => isEmpty.value && !hasNextPage.value)
const showLoading = computed(() => isPending.value && isEmpty.value)
const showError = computed(() => isError.value && nothingLeft.value)

/*
 * 벨이 있는 지갑 홈으로 돌아간다.
 *
 * `router.back()`을 쓰지 않는다. 정산 상세에서 뒤로 나올 때 이 화면을 **새로 쌓기**
 * 때문에, 그 다음 뒤로 가기는 방금 빠져나온 정산 상세로 되돌아간다. 사용자는 두 화면
 * 사이를 오가며 지갑으로 나가지 못한다.
 *
 * 이 화면은 지갑 홈의 벨에서만 들어오므로 돌아갈 곳도 하나뿐이다. 정산 목록이 같은
 * 이유로 지갑 홈을 직접 가리킨다.
 */
function goBack(): void {
  void router.push({ name: 'wallet' })
}

function loadMore(): void {
  if (!hasNextPage.value || isFetchingNextPage.value) return
  void fetchNextPage()
}

function dismiss(notification: AppNotification): void {
  deleteOne.mutate(notification.id)
}

function markAllRead(): void {
  readAll.mutate()
}

function dismissAll(): void {
  deleteAll.mutate()
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
  if (!notification.isRead) readOne.mutate(notification.id)

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
  <main class="flex w-full flex-col px-screen flex-1 pt-6 pb-8">
    <ScreenHeader
      variant="back"
      :title="t('notification.title')"
      :back-label="t('action.back')"
      @back="goBack"
    />

    <!--
      두 가지 일괄 동작은 헤더가 아니라 그 아래 줄에 둔다. 390px 폭에서 제목과 나란히
      세우면 셋 다 잘린다. 할 일이 없을 때는 아예 보이지 않게 해서 줄 자체가 사라진다.
    -->
    <div
      v-if="!isEmpty"
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
      v-if="showLoading"
      class="mt-8"
    />

    <StateError
      v-else-if="showError"
      class="my-auto"
      :description="errorDescription()"
    />

    <StateEmpty
      v-else-if="nothingLeft"
      class="my-auto"
      :title="t('notification.empty.title')"
      :description="t('notification.empty.description')"
    />

    <template v-else>
      <ul class="mt-5 flex flex-col gap-2">
        <li
          v-for="notification in notifications"
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
        v-if="hasNextPage"
        class="mt-4"
        block
        variant="secondary"
        :disabled="isFetchingNextPage"
        data-testid="notification-load-more"
        @click="loadMore"
      >
        {{ isFetchingNextPage ? t('notification.loadingMore') : t('notification.loadMore') }}
      </AppButton>

      <!--
        다음 쪽만 실패한 경우다. 앞 쪽은 그대로 두고 여기서만 알린다 — 화면을 오류로 덮으면
        이미 읽고 있던 목록까지 사라진다.
      -->
      <p
        v-if="isError"
        class="mt-3 text-center text-caption text-ink-3"
        data-testid="notification-load-more-error"
      >
        {{ errorDescription() ?? t('error.unknown') }}
      </p>
    </template>
  </main>
</template>
