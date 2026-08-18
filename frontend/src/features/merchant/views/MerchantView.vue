<script setup lang="ts">
import QRCode from 'qrcode'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
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

const accountQuery = useMerchantAccount()
const isMerchant = computed(() => accountQuery.data.value?.accountType === 'MERCHANT')

/* ---------------------------------- 등록 ---------------------------------- */

const businessName = ref('')

const registerMutation = useMutation({
  mutationFn: (name: string) => registerAsMerchant(name),
  onSuccess: () => {
    /*
     * 계정 유형이 바뀌면 이전에 받아 둔 응답의 전제가 모두 달라진다. 특히 라우터 guard가
     * 보는 회원 프로필 캐시가 TRAVELER로 남아 있으면 등록 직후 손님 화면으로 나갈 수 있다.
     * feature 간 import 금지 때문에 그 캐시를 직접 지정할 수 없으므로, 로그인 콜백이 쓰는
     * 것과 같은 방식으로 캐시 전체를 버린다. 활성 쿼리는 곧바로 다시 받아 온다.
     */
    queryClient.clear()
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

const formattedIncome = computed(() =>
  new Intl.NumberFormat(locale.value, { maximumFractionDigits: 2 }).format(incomeTotal.value),
)

/* --------------------------------- QR 생성 --------------------------------- */

const amount = ref<number | null>(null)
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

const canCreate = computed(
  () => amount.value !== null && amount.value > 0 && !createMutation.isPending.value,
)

const createQr = (): void => {
  if (!canCreate.value || amount.value === null) return

  const trimmedMemo = memo.value.trim()

  createMutation.mutate({ value: amount.value, note: trimmedMemo === '' ? null : trimmedMemo })
}

const resetQr = (): void => {
  activeQr.value = null
  qrImageSrc.value = null
  amount.value = null
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
              <AmountInput
                v-model="amount"
                :label="t('merchant.qr.amount')"
                :placeholder="t('merchant.qr.amountPlaceholder')"
              />
              <TextInput
                v-model="memo"
                :label="t('merchant.qr.memo')"
                :placeholder="t('merchant.qr.memoPlaceholder')"
              />
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
