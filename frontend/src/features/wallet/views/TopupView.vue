<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { IconCheck } from '@tabler/icons-vue'

import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import { createStripeIntent, getTopupMethods, previewTopup } from '../api/topupApi'
import {
  DEFAULT_TOPUP_METHOD,
  formatKrw,
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

// AmountInput의 계약이 number | null이다. null은 "아직 입력 전"이고 0과 구분된다.
const amount = ref<number | null>(null)
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
  amount.value = nextAmount
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

  void router.push({ name: 'wallet' })
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
      <h1 class="min-w-0 flex-1 truncate font-display text-screen-title uppercase text-ink-display">
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
          :label="t('wallet.topUp.amountLabel')"
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
              +{{ formatKrw(quickAmount) }}
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
            <dd class="font-semibold text-ink">{{ formatKrw(preview.amount) }}</dd>
          </div>
          <div class="flex items-center justify-between py-3">
            <dt class="text-ink-2">{{ t('wallet.topUp.fee') }}</dt>
            <dd class="font-semibold text-ink">{{ formatKrw(preview.fee) }}</dd>
          </div>
          <div class="flex items-center justify-between py-3">
            <dt class="text-ink-2">{{ t('wallet.topUp.paymentMethod') }}</dt>
            <dd class="font-semibold text-ink">{{ selectedMethodLabel }} (Test Mode)</dd>
          </div>
          <div class="flex items-center justify-between pb-0 pt-3">
            <dt class="text-ink-2">{{ t('wallet.topUp.balanceAfter') }}</dt>
            <dd class="font-semibold text-ink">{{ formatKrw(preview.expectedSandboxBalance) }}</dd>
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
      <p class="mt-3 text-data-xl text-success">+{{ formatKrw(preview?.amount ?? 0) }}</p>
      <p class="mt-2 text-body-sm text-ink-2">
        {{ t('wallet.topUp.currentBalance', { balance: formatKrw(completedBalance) }) }}
      </p>

      <AppButton
        block
        class="mt-auto"
        @click="goBack"
      >
        {{ t('wallet.topUp.backToWallet') }}
      </AppButton>
    </section>
  </main>
</template>
