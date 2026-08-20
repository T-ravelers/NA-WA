<script setup lang="ts">
import QRCode from 'qrcode'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { IconTrash } from '@tabler/icons-vue'
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { NormalizedApiError } from '@/shared/api/apiError'
import { parseServerDateTime } from '@/shared/lib/datetime'
import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import TextInput from '@/shared/ui/TextInput.vue'

import { createMerchantQr, registerAsMerchant, type MerchantQr } from '../api/merchantApi'
import {
  calculateTotal,
  createEmptyItem,
  isValidTotal,
  itemSubtotal,
  parseQuantity,
  type MerchantQrItem,
} from '../model/merchantQr'
import {
  creditEntries,
  merchantKeys,
  sumIncome,
  todayRange,
  useMerchantAccount,
  useMerchantIncome,
} from '../model/merchantQueries'

/**
 * 가맹점 화면.
 *
 * 등록 전후를 한 화면에서 처리한다. 소셜 로그인은 계정을 항상 `TRAVELER`로 만들기
 * 때문에, 가맹점 링크로 처음 들어온 사람은 여기서 상호명을 넣어야 가맹점이 된다.
 */
const i18n = useI18n()
const { t, locale } = i18n
const queryClient = useQueryClient()

const formatAmount = (value: number): string =>
  new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2 }).format(value)

const accountQuery = useMerchantAccount()
const isMerchant = computed(() => accountQuery.data.value?.accountType === 'MERCHANT')

/* ---------------------------------- 등록 ---------------------------------- */

const businessName = ref('')

const registerMutation = useMutation({
  mutationFn: (name: string) => registerAsMerchant(name),
  onSuccess: (account) => {
    /*
     * 계정 유형이 바뀌면 이전에 받아 둔 응답의 전제가 모두 달라진다. 특히 라우터 guard가
     * 보는 회원 프로필 캐시가 TRAVELER로 남아 있으면 등록 직후 손님 화면으로 나갈 수 있다.
     * guard의 ensureQueryData는 값이 있으면 stale이어도 그대로 돌려주므로 무효화로는
     * 부족하고 캐시에서 지워야 한다. feature 간 import 금지 때문에 남의 캐시 키를 여기서
     * 지정할 수 없어, 이 화면 것만 남기고 전부 버린다.
     */
    queryClient.removeQueries({
      predicate: (query) => query.queryKey[0] !== merchantKeys.all[0],
    })

    /*
     * 이 화면은 응답으로 직접 갱신한다. 캐시를 지우는 것만으로는 활성 쿼리가 다시 그려진다는
     * 보장이 없어, 등록에 성공하고도 등록 폼이 남고 다시 누르면 MEMBER-009를 받는다.
     */
    queryClient.setQueryData(merchantKeys.account(), account)
  },
})

const canRegister = computed(
  () => businessName.value.trim() !== '' && !registerMutation.isPending.value,
)

const submitRegistration = (): void => {
  if (!canRegister.value) return

  registerMutation.mutate(businessName.value.trim())
}

/* --------------------------------- 매출 조회 -------------------------------- */

const range = ref(todayRange())
const incomeQuery = useMerchantIncome(range, isMerchant)

// 합계와 건수는 같은 목록에서 나와야 한다. 한쪽만 방향을 거르면 서로 어긋난다.
const incomeEntries = computed(() => creditEntries(incomeQuery.data.value))
const incomeTotal = computed(() => sumIncome(incomeEntries.value))
const incomeCount = computed(() => incomeEntries.value.length)

const formattedIncome = computed(() => formatAmount(incomeTotal.value))

/* --------------------------------- QR 생성 --------------------------------- */

/*
 * 품목 입력은 화면 안에서만 산다. 서버로 보내지 않으므로 Vue Query가 아니라 ref가 소유한다.
 * QR에는 계산된 합계만 실린다.
 */
const items = ref<MerchantQrItem[]>([createEmptyItem()])
const memo = ref('')
const activeQr = ref<MerchantQr | null>(null)
const qrImageSrc = ref<string | null>(null)
const remainingMs = ref(0)

const createMutation = useMutation({
  mutationFn: ({ value, note }: { value: number; note: string | null }) =>
    createMerchantQr(value, note),
  onSuccess: (qr) => {
    activeQr.value = qr
    // 결제가 들어오면 매출에 반영돼야 한다. 만료를 기다리지 않고 다음 조회를 새로 받는다.
    void queryClient.invalidateQueries({ queryKey: merchantKeys.income() })
  },
})

const totalAmount = computed(() => calculateTotal(items.value))

const canCreate = computed(() => isValidTotal(totalAmount.value) && !createMutation.isPending.value)

const addItem = (): void => {
  items.value.push(createEmptyItem())
}

const removeItem = (id: number): void => {
  const remaining = items.value.filter((item) => item.id !== id)

  // 줄이 하나도 없으면 입력할 곳이 사라진다. 마지막 줄은 비우기만 한다.
  items.value = remaining.length === 0 ? [createEmptyItem()] : remaining
}

const createQr = (): void => {
  if (!canCreate.value) return

  const trimmedMemo = memo.value.trim()

  createMutation.mutate({
    value: totalAmount.value,
    note: trimmedMemo === '' ? null : trimmedMemo,
  })
}

const resetQr = (): void => {
  activeQr.value = null
  qrImageSrc.value = null
  items.value = [createEmptyItem()]
  memo.value = ''
}

watch(
  () => activeQr.value?.qrToken,
  (qrToken) => {
    if (qrToken === undefined) {
      qrImageSrc.value = null
      return
    }

    void QRCode.toDataURL(qrToken, { margin: 1, width: 320 }).then((dataUrl) => {
      qrImageSrc.value = dataUrl
    })
  },
  { immediate: true },
)

/**
 * 만료 카운트다운.
 *
 * QR은 생성 후 1분이면 만료된다. 남은 시간을 보여주지 않으면 가맹점주가 이미 죽은 코드를
 * 계속 내밀게 된다. 만료 시각은 서버가 내려준 `expiresAt`이 정본이다.
 */
let timerId: ReturnType<typeof setInterval> | null = null

const stopTimer = (): void => {
  if (timerId !== null) {
    clearInterval(timerId)
    timerId = null
  }
}

watch(
  () => activeQr.value?.expiresAt,
  (expiresAt) => {
    stopTimer()

    if (expiresAt === undefined) {
      remainingMs.value = 0
      return
    }

    // `expiresAt`은 오프셋이 없는 KST 벽시계다. `new Date()`로 읽으면 기기 시간대로
    // 해석돼 이미 만료된 QR이 한참 남은 것처럼 보인다.
    const expiresAtDate = parseServerDateTime(expiresAt)

    if (expiresAtDate === null) {
      remainingMs.value = 0
      return
    }

    const expiresAtMs = expiresAtDate.getTime()
    const tick = (): void => {
      remainingMs.value = Math.max(0, expiresAtMs - Date.now())

      if (remainingMs.value === 0) stopTimer()
    }

    tick()
    timerId = setInterval(tick, 1000)
  },
  { immediate: true },
)

onUnmounted(stopTimer)

const isExpired = computed(() => activeQr.value !== null && remainingMs.value === 0)

const remainingLabel = computed(() => {
  const totalSeconds = Math.ceil(remainingMs.value / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60

  return t('merchant.qr.validity', { time: `${minutes}:${String(seconds).padStart(2, '0')}` })
})

/* --------------------------------- 오류 문구 -------------------------------- */

/** 오류 분기는 메시지가 아니라 `error.code`로 한다. 모르는 코드는 화면 기본 문구로 폴백한다. */
function toMessage(error: unknown, fallbackKey: string): string {
  if (error instanceof NormalizedApiError) {
    const key = `merchant.errorCode.${error.code}`

    if (i18n.te(key)) return t(key)
  }

  return t(fallbackKey)
}

const registerError = computed(() =>
  registerMutation.error.value === null
    ? undefined
    : toMessage(registerMutation.error.value, 'merchant.register.error'),
)

const createError = computed(() =>
  createMutation.error.value === null
    ? null
    : toMessage(createMutation.error.value, 'merchant.qr.error'),
)
</script>

<template>
  <main class="min-h-dvh bg-canvas px-screen pb-8 text-ink">
    <header class="border-b border-hairline py-4">
      <h1 class="text-center text-title font-bold tracking-[-0.03em]">
        {{ t('merchant.title') }}
      </h1>
    </header>

    <StateLoading v-if="accountQuery.isPending.value" />

    <StateError
      v-else-if="accountQuery.isError.value"
      :action-label="t('merchant.income.retry')"
      @retry="accountQuery.refetch()"
    />

    <!-- 아직 가맹점이 아니다. 상호명을 확정하는 것이 가맹점 회원가입이다. -->
    <section
      v-else-if="!isMerchant"
      class="pt-6"
      aria-labelledby="merchant-register-heading"
    >
      <AppCard padding="lg">
        <h2
          id="merchant-register-heading"
          class="text-title-sm"
        >
          {{ t('merchant.register.heading') }}
        </h2>
        <p class="mt-1 text-caption text-ink-3">
          {{ t('merchant.register.description') }}
        </p>
        <p class="mt-3 rounded-sm bg-surface-2 px-3 py-2 text-caption text-ink-2">
          {{ t('merchant.register.irreversible') }}
        </p>

        <form
          class="mt-5 space-y-4"
          @submit.prevent="submitRegistration"
        >
          <TextInput
            v-model="businessName"
            :label="t('merchant.register.businessName')"
            :placeholder="t('merchant.register.businessNamePlaceholder')"
            :error="registerError"
          />
          <AppButton
            type="submit"
            block
            :disabled="!canRegister"
            :loading="registerMutation.isPending.value"
          >
            {{ t('merchant.register.submit') }}
          </AppButton>
        </form>
      </AppCard>
    </section>

    <template v-else>
      <section
        class="pt-6"
        aria-labelledby="merchant-income-heading"
      >
        <AppCard
          padding="lg"
          tone="paper"
        >
          <h2
            id="merchant-income-heading"
            class="text-caption"
          >
            {{ t('merchant.income.heading') }}
          </h2>

          <StateLoading v-if="incomeQuery.isPending.value" />

          <StateError
            v-else-if="incomeQuery.isError.value"
            :description="t('merchant.income.error')"
            :action-label="t('merchant.income.retry')"
            @retry="incomeQuery.refetch()"
          />

          <template v-else>
            <p class="mt-2 text-display font-bold tabular-nums">
              {{ t('merchant.income.amount', { amount: formattedIncome }) }}
            </p>
            <p class="mt-1 text-caption">
              {{ t('merchant.income.count', { count: incomeCount }, incomeCount) }}
            </p>
            <p
              v-if="incomeCount === 0"
              class="mt-1 text-caption"
            >
              {{ t('merchant.income.empty') }}
            </p>
          </template>
        </AppCard>
      </section>

      <section
        class="pt-4"
        aria-labelledby="merchant-qr-heading"
      >
        <AppCard padding="lg">
          <h2
            id="merchant-qr-heading"
            class="text-title-sm"
          >
            {{ t('merchant.qr.heading') }}
          </h2>

          <!-- QR을 만들기 전: 금액과 메모를 받는다. -->
          <template v-if="activeQr === null">
            <p class="mt-1 text-caption text-ink-3">
              {{ t('merchant.qr.description') }}
            </p>

            <form
              class="mt-5 space-y-4"
              @submit.prevent="createQr"
            >
              <!--
                품목·수량·단가는 서버로 보내지 않는다. 합계를 손으로 더하지 않게 돕는
                입력 보조이며, QR에는 아래에서 계산된 합계 금액만 실린다.
              -->
              <ul class="space-y-4">
                <li
                  v-for="(item, index) in items"
                  :key="item.id"
                  class="rounded-sm border border-hairline p-3"
                >
                  <div class="flex items-end gap-2">
                    <div class="min-w-0 flex-1">
                      <TextInput
                        v-model="item.name"
                        :label="t('merchant.qr.itemName')"
                        :placeholder="t('merchant.qr.itemNamePlaceholder')"
                      />
                    </div>
                    <button
                      type="button"
                      class="grid size-13 shrink-0 place-items-center rounded-sm text-ink-3 transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
                      :aria-label="t('merchant.qr.removeItem', { index: index + 1 })"
                      @click="removeItem(item.id)"
                    >
                      <IconTrash
                        :size="20"
                        :stroke-width="2"
                        aria-hidden="true"
                      />
                    </button>
                  </div>

                  <div class="mt-3 grid grid-cols-[5rem_1fr] items-end gap-2">
                    <!-- 수량은 통화가 아니라 AmountInput을 쓰지 않고, TextInput은 숫자 바인딩이 없다. -->
                    <div class="flex flex-col gap-1.5">
                      <label
                        :for="`merchant-qty-${item.id}`"
                        class="text-caption text-ink-2"
                      >
                        {{ t('merchant.qr.quantity') }}
                      </label>
                      <input
                        :id="`merchant-qty-${item.id}`"
                        type="text"
                        inputmode="numeric"
                        :value="item.quantity ?? ''"
                        class="h-14 w-full rounded-sm border-2 border-transparent bg-surface-2 px-4 text-right text-data-lg text-ink outline-none focus-visible:border-ink"
                        @input="
                          item.quantity = parseQuantity(($event.target as HTMLInputElement).value)
                        "
                      />
                    </div>
                    <AmountInput
                      v-model="item.unitPrice"
                      :label="t('merchant.qr.unitPrice')"
                      :placeholder="t('merchant.qr.unitPricePlaceholder')"
                    />
                  </div>

                  <p class="mt-2 text-right text-caption text-ink-3 tabular-nums">
                    {{ t('merchant.qr.subtotal', { amount: formatAmount(itemSubtotal(item)) }) }}
                  </p>
                </li>
              </ul>

              <AppButton
                variant="secondary"
                block
                @click="addItem"
              >
                {{ t('merchant.qr.addItem') }}
              </AppButton>

              <TextInput
                v-model="memo"
                :label="t('merchant.qr.memo')"
                :placeholder="t('merchant.qr.memoPlaceholder')"
              />

              <!-- 합계는 QR에 실제로 실리는 값이라 맨 아래에서 크게 보여준다. -->
              <div
                class="flex items-baseline justify-between border-t border-hairline pt-4"
                role="status"
              >
                <span class="text-body">{{ t('merchant.qr.total') }}</span>
                <span class="text-title font-bold tabular-nums">
                  {{ t('merchant.qr.totalAmount', { amount: formatAmount(totalAmount) }) }}
                </span>
              </div>
              <p
                v-if="totalAmount === 0"
                class="text-caption text-ink-3"
              >
                {{ t('merchant.qr.totalHint') }}
              </p>
              <p
                v-if="createError !== null"
                class="text-caption text-danger"
                role="alert"
              >
                {{ createError }}
              </p>
              <AppButton
                type="submit"
                block
                :disabled="!canCreate"
                :loading="createMutation.isPending.value"
              >
                {{ t('merchant.qr.create') }}
              </AppButton>
            </form>
          </template>

          <!-- QR을 만든 뒤: 코드와 남은 시간을 보여준다. -->
          <template v-else>
            <div class="mt-5 flex flex-col items-center gap-3">
              <img
                v-if="qrImageSrc !== null"
                :src="qrImageSrc"
                :alt="t('merchant.qr.imageAlt')"
                class="size-64 max-w-full rounded-sm bg-white p-2"
                :class="isExpired ? 'opacity-40' : ''"
              />
              <p
                class="text-caption tabular-nums"
                :class="isExpired ? 'text-danger' : 'text-ink-3'"
                role="status"
              >
                {{ isExpired ? t('merchant.qr.expired') : remainingLabel }}
              </p>
              <AppButton
                block
                :variant="isExpired ? 'primary' : 'secondary'"
                @click="resetQr"
              >
                {{ isExpired ? t('merchant.qr.expiredAction') : t('merchant.qr.createAnother') }}
              </AppButton>
            </div>
          </template>
        </AppCard>
      </section>
    </template>
  </main>
</template>
