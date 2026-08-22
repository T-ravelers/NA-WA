<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import { IconCheck } from '@tabler/icons-vue'

import { vFitText } from '@/shared/lib/fitText'
import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import { createStripeIntent, getTopupMethods, previewTopup } from '../api/topupApi'
import {
  DEFAULT_TOPUP_METHOD,
  formatPoints,
  getTopupMethodLabel,
  QUICK_TOPUP_AMOUNTS,
  TOPUP_CURRENCY,
  topupKeys,
  type TopupMethod,
  type TopupPreviewResponse,
  type StripeIntentResponse,
  type StripeTopupStatusResponse,
} from '../model/topup'
import StripePaymentStep from './StripePaymentStep.vue'

type TopupStep = 'form' | 'preview' | 'payment' | 'complete'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

/**
 * 다른 화면이 "이만큼 부족하다"며 보낸 금액(`?amount=`). 약속 생성이 보증금을
 * 예치할 잔액이 없을 때 그 보증금만큼 채워서 보낸다. 양의 정수가 아니면 무시한다.
 */
function requestedAmount(): number | null {
  const raw = Array.isArray(route.query.amount) ? route.query.amount[0] : route.query.amount
  const parsed = Number(raw)
  return typeof raw === 'string' && Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

/**
 * 다른 화면이 "충전이 끝나면 돌아와 달라"며 보낸 곳(`?returnRouteName=`). 여정 생성이
 * 쓰는 것과 같은 규약이다. 나머지 query는 그 화면의 것이라 그대로 돌려주고, 이 화면의
 * 금액과 이 키만 뺀다. `resume=1`을 붙여 그 화면이 저장해 둔 초안을 되살리게 한다.
 */
const returnTarget = computed<RouteLocationRaw | null>(() => {
  const value = route.query.returnRouteName
  if (typeof value !== 'string' || value === '') return null
  const rest = { ...route.query }
  delete rest.returnRouteName
  delete rest.amount
  return { name: value, query: { ...rest, resume: '1' } }
})

/**
 * 이 화면을 떠나 왔던 자리로 돌아간다. **되감아서 이 화면의 히스토리 엔트리를
 * 소비한다.** 충전으로 보낸 화면은 떠나기 전에 자기 엔트리를 돌아올 자리(`resume=1`)로
 * 바꿔 두므로, 바로 아래 엔트리가 이미 목적지다. 여기서 목적지를 다시 `replace`하면
 * 같은 화면이 히스토리에 두 번 쌓여, 돌아간 화면에서 뒤로 가기를 눌러도 흐름을 벗어나지
 * 못하고 같은 라우트에 머문다 — 그 자리는 초안을 이미 지운 뒤라 빈 폼으로 열린다.
 *
 * 되감을 히스토리가 없을 때(딥링크·PWA 재진입)만 목적지를 만들어 보낸다. 보낸 화면이
 * 있으면 그리로(`resume=1`), 없으면 지갑 탭이다.
 */
const leaveTopup = (): void => {
  if (window.history.length > 1) {
    void router.back()
    return
  }
  if (returnTarget.value !== null) {
    void router.replace(returnTarget.value)
    return
  }
  void router.push({ name: 'wallet' })
}

// AmountInput의 계약이 number | null이다. null은 "아직 입력 전"이고 0과 구분된다.
const amount = ref<number | null>(requestedAmount())
const selectedMethod = ref(DEFAULT_TOPUP_METHOD)
const step = ref<TopupStep>('form')
const preview = ref<TopupPreviewResponse | null>(null)
const idempotencyKey = ref<string | null>(null)
const stripeIntent = ref<StripeIntentResponse | null>(null)
const completedBalance = ref<string | number>(0)
const paymentError = ref('')

const methodsQuery = useQuery({
  queryKey: topupKeys.methods(),
  queryFn: getTopupMethods,
})

const previewMutation = useMutation({
  mutationFn: previewTopup,
})

const stripeIntentMutation = useMutation({
  mutationFn: ({ amount, requestKey }: { amount: number; requestKey: string }) =>
    createStripeIntent({ amount, currency: TOPUP_CURRENCY }, requestKey),
})

const enabledMethods = computed(
  () => methodsQuery.data.value?.methods.filter((method) => method.enabled) ?? [],
)

const selectedMethodData = computed<TopupMethod | null>(
  () => enabledMethods.value.find((method) => method.type === selectedMethod.value) ?? null,
)

const selectedMethodLabel = computed(() =>
  selectedMethodData.value ? getTopupMethodLabel(selectedMethodData.value) : 'Stripe',
)

const getTopupMethodLogo = (methodType: string): string =>
  methodType === DEFAULT_TOPUP_METHOD ? '/payment/stripe-mark.svg' : '/payment/paypal-mark.svg'

const canContinue = computed(
  () =>
    amount.value !== null &&
    amount.value > 0 &&
    selectedMethodData.value !== null &&
    !previewMutation.isPending.value,
)

watch(enabledMethods, (methods) => {
  if (methods.length > 0 && !methods.some((method) => method.type === selectedMethod.value)) {
    selectedMethod.value = methods[0]?.type ?? DEFAULT_TOPUP_METHOD
  }
})

const setAmount = (nextAmount: number): void => {
  amount.value = (amount.value ?? 0) + nextAmount
}

const submitPreview = (): void => {
  const amountValue = amount.value
  if (!canContinue.value || amountValue === null) return

  previewMutation.mutate(
    {
      amount: amountValue,
      method: selectedMethod.value,
      currency: TOPUP_CURRENCY,
    },
    {
      onSuccess: (response) => {
        preview.value = response
        step.value = 'preview'
      },
    },
  )
}

const goBack = (): void => {
  if (step.value === 'preview') {
    step.value = 'form'
    idempotencyKey.value = null
    stripeIntentMutation.reset()
    return
  }

  if (step.value === 'payment') {
    step.value = 'preview'
    paymentError.value = ''
    return
  }

  if (step.value === 'complete') {
    void router.push({ name: 'wallet' })
    return
  }

  // 충전을 시작하지도 않고 나가는 길. 왔던 길을 되감는다 — 약속 생성처럼 흐름 도중에
  // 들렀다면 지갑 탭으로 떨어지는 순간 작성하던 흐름이 끊기고, 저장해 둔 초안은 아무도
  // 읽지 않는다. 모바일 PWA에는 브라우저 뒤로가기가 없어 이 버튼이 사실상 유일한
  // 출구다. 완료 화면은 예외다 — 거기엔 "Continue where you left off"가 따로 있고,
  // 이 버튼은 지갑으로 가는 쪽이 맞다.
  leaveTopup()
}

const executeTopup = (): void => {
  if (preview.value === null) return

  const requestKey =
    idempotencyKey.value ?? globalThis.crypto?.randomUUID?.() ?? `topup-${Date.now()}`

  idempotencyKey.value = requestKey

  stripeIntentMutation.mutate(
    { amount: Number(preview.value.amount), requestKey },
    {
      onSuccess: (response) => {
        stripeIntent.value = response
        paymentError.value = ''
        step.value = 'payment'
      },
    },
  )
}

const handlePaymentConfirmed = (status: StripeTopupStatusResponse): void => {
  completedBalance.value = status.sandboxBalance
  step.value = 'complete'
}

const handlePaymentError = (message: string): void => {
  paymentError.value = message
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col px-screen pb-8 pt-6">
    <header class="flex items-center gap-3">
      <AppButton
        compact
        variant="secondary"
        :aria-label="t('wallet.topUp.back')"
        @click="goBack"
      >
        ‹
      </AppButton>
      <h1
        v-fit-text
        class="min-w-0 flex-1 truncate font-display text-screen-title uppercase text-ink-display"
      >
        {{
          step === 'preview'
            ? t('wallet.topUp.previewTitle')
            : step === 'payment'
              ? t('wallet.topUp.paymentTitle')
              : t('wallet.topUp.title')
        }}
      </h1>
    </header>

    <section
      v-if="step === 'form'"
      class="mt-5 flex flex-1 flex-col gap-2"
      aria-labelledby="top-up-form-title"
    >
      <h2
        id="top-up-form-title"
        class="sr-only"
      >
        {{ t('wallet.topUp.title') }}
      </h2>

      <AppCard>
        <AmountInput
          v-model="amount"
          currency-symbol="P"
          symbol-position="suffix"
          :label="t('wallet.topUp.amountLabel')"
          :helper="t('wallet.topUp.pointsRateNotice')"
        />

        <div class="mt-4 border-t border-hairline pt-4">
          <div class="grid grid-cols-2 gap-2.5">
            <button
              v-for="quickAmount in QUICK_TOPUP_AMOUNTS"
              :key="quickAmount"
              type="button"
              class="h-10 rounded-pill border border-hairline-strong text-body-sm font-medium text-ink transition-colors hover:border-paper-fill focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
              @click="setAmount(quickAmount)"
            >
              +{{ formatPoints(quickAmount) }}
            </button>
          </div>
        </div>
      </AppCard>

      <AppCard>
        <h2 class="text-body-sm text-ink-2">{{ t('wallet.topUp.paymentMethod') }}</h2>

        <p
          v-if="methodsQuery.isPending.value"
          class="mt-4 text-body-sm text-ink-2"
        >
          {{ t('wallet.topUp.loadingMethods') }}
        </p>
        <p
          v-else-if="methodsQuery.isError.value"
          class="mt-4 text-body-sm text-danger"
        >
          {{ t('wallet.topUp.methodsError') }}
        </p>
        <div
          v-else
          class="mt-3 space-y-2"
        >
          <button
            v-for="method in enabledMethods"
            :key="method.type"
            type="button"
            class="w-full rounded-sm border p-4 text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
            :class="selectedMethod === method.type ? 'border-paper-fill' : 'border-hairline'"
            :aria-pressed="selectedMethod === method.type"
            @click="selectedMethod = method.type"
          >
            <span class="flex items-center gap-3 text-body-sm font-semibold text-ink">
              <img
                :src="getTopupMethodLogo(method.type)"
                alt=""
                class="size-8 shrink-0 rounded-sm"
              />
              <span>
                {{ getTopupMethodLabel(method) }}
                <span
                  v-if="method.testMode"
                  class="ml-1 rounded bg-paper-fill px-1.5 py-0.5 text-micro text-on-paper"
                >
                  Test
                </span>
              </span>
            </span>
            <span class="mt-1 block text-caption text-ink-2">
              {{ t('wallet.topUp.cardDescription') }}
            </span>
          </button>

          <!-- 시안(충전하기/01)의 비활성 수단 조형 — 캔버스색 면을 55% 투명도로 가라앉힌다. -->
          <div class="rounded-sm bg-canvas p-4 text-left opacity-55">
            <span class="flex items-center gap-3 text-body-sm font-semibold text-ink">
              <img
                :src="getTopupMethodLogo('PAYPAL')"
                alt=""
                class="size-8 shrink-0 rounded-sm"
              />
              <span>
                PayPal
                <span class="ml-1 rounded bg-surface-2 px-1.5 py-0.5 text-micro text-ink-2">
                  Coming soon
                </span>
              </span>
            </span>
            <span class="mt-1 block text-caption text-ink-2">
              {{ t('wallet.topUp.paypalDescription') }}
            </span>
          </div>
        </div>

        <p class="mt-3 rounded-md bg-canvas px-4 py-3 text-caption text-ink-2">
          {{ t('wallet.topUp.sandboxNotice') }}
        </p>
      </AppCard>

      <p
        v-if="previewMutation.isError.value"
        class="mt-2 text-body-sm text-danger"
        role="alert"
      >
        {{ t('wallet.topUp.previewError') }}
      </p>

      <AppButton
        block
        class="mt-auto"
        :disabled="!canContinue"
        :loading="previewMutation.isPending.value"
        @click="submitPreview"
      >
        {{
          previewMutation.isPending.value
            ? t('wallet.topUp.loadingPreview')
            : t('wallet.topUp.next')
        }}
      </AppButton>
    </section>

    <section
      v-else-if="step === 'preview' && preview"
      class="mt-5 flex flex-1 flex-col"
      aria-labelledby="top-up-preview-title"
    >
      <AppCard>
        <h2
          id="top-up-preview-title"
          class="text-title-sm text-ink"
        >
          {{ t('wallet.topUp.previewHeading') }}
        </h2>

        <dl class="mt-4 divide-y divide-hairline text-body">
          <div class="flex items-center justify-between py-3 first:pt-0">
            <dt class="text-ink-2">{{ t('wallet.topUp.amountLabel') }}</dt>
            <dd class="font-semibold text-ink">{{ formatPoints(preview.amount) }}</dd>
          </div>
          <div class="flex items-center justify-between py-3">
            <dt class="text-ink-2">{{ t('wallet.topUp.fee') }}</dt>
            <dd class="font-semibold text-ink">{{ formatPoints(preview.fee) }}</dd>
          </div>
          <div class="flex items-center justify-between py-3">
            <dt class="text-ink-2">{{ t('wallet.topUp.paymentMethod') }}</dt>
            <dd class="font-semibold text-ink">{{ selectedMethodLabel }} (Test Mode)</dd>
          </div>
          <div class="flex items-center justify-between pb-0 pt-3">
            <dt class="text-ink-2">{{ t('wallet.topUp.balanceAfter') }}</dt>
            <dd class="font-semibold text-ink">
              {{ formatPoints(preview.expectedSandboxBalance) }}
            </dd>
          </div>
        </dl>
      </AppCard>

      <p
        v-if="preview.warning"
        class="mt-3 text-body-sm text-warning"
      >
        {{ t('wallet.topUp.walletWarning') }}
      </p>

      <p
        v-if="stripeIntentMutation.isError.value"
        class="mt-3 text-body-sm text-danger"
        role="alert"
      >
        {{ t('wallet.topUp.paymentRequestError') }}
      </p>

      <div class="mt-auto grid grid-cols-2 gap-3">
        <AppButton
          variant="secondary"
          @click="step = 'form'"
        >
          {{ t('wallet.topUp.previous') }}
        </AppButton>
        <AppButton
          :disabled="stripeIntentMutation.isPending.value"
          :loading="stripeIntentMutation.isPending.value"
          @click="executeTopup"
        >
          {{
            stripeIntentMutation.isPending.value
              ? t('wallet.topUp.loadingPaymentRequest')
              : t('wallet.topUp.execute')
          }}
        </AppButton>
      </div>
    </section>

    <section
      v-else-if="step === 'payment' && stripeIntent"
      class="mt-5 flex flex-1 flex-col"
      aria-labelledby="stripe-payment-title"
    >
      <h2
        id="stripe-payment-title"
        class="sr-only"
      >
        {{ t('wallet.topUp.paymentTitle') }}
      </h2>
      <StripePaymentStep
        :client-secret="stripeIntent.clientSecret"
        :topup-id="stripeIntent.topupId"
        :amount="stripeIntent.amount"
        @payment-confirmed="handlePaymentConfirmed"
        @payment-error="handlePaymentError"
        @back="goBack"
      />
      <p
        v-if="paymentError"
        class="sr-only"
        aria-live="polite"
      >
        {{ paymentError }}
      </p>
    </section>

    <section
      v-else-if="step === 'complete'"
      class="mt-5 flex flex-1 flex-col items-center justify-center text-center"
      aria-labelledby="top-up-complete-title"
      aria-live="polite"
    >
      <div
        class="grid size-16 place-items-center rounded-pill border-2 border-hairline-strong text-ink"
        aria-hidden="true"
      >
        <IconCheck
          :size="28"
          aria-hidden="true"
        />
      </div>
      <h2
        id="top-up-complete-title"
        class="mt-5 text-title text-ink"
      >
        {{ t('wallet.topUp.completeTitle') }}
      </h2>
      <p class="mt-3 text-data-xl text-success">+{{ formatPoints(preview?.amount ?? 0) }}</p>
      <p class="mt-2 text-body-sm text-ink-2">
        {{ t('wallet.topUp.currentBalance', { balance: formatPoints(completedBalance) }) }}
      </p>

      <div class="mt-auto flex w-full flex-col gap-2">
        <AppButton
          v-if="returnTarget !== null"
          block
          @click="leaveTopup"
        >
          {{ t('wallet.topUp.backToCaller') }}
        </AppButton>
        <AppButton
          block
          :variant="returnTarget === null ? 'primary' : 'secondary'"
          @click="goBack"
        >
          {{ t('wallet.topUp.backToWallet') }}
        </AppButton>
      </div>
    </section>
  </main>
</template>
