<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

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

const amount = ref(0)
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
  () => amount.value > 0 && selectedMethodData.value !== null && !previewMutation.isPending.value,
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
  if (!canContinue.value) return

  previewMutation.mutate(
    {
      amount: amount.value,
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
  <main class="min-h-dvh bg-[#151515] text-[#f5f4f0]">
    <header class="flex items-center border-b border-[#2d2d2d] px-5 py-4">
      <button
        type="button"
        class="grid size-8 place-items-center text-2xl leading-none text-[#f5f4f0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
        :aria-label="t('wallet.topUp.back')"
        @click="goBack"
      >
        ‹
      </button>
      <h1 class="flex-1 text-center text-lg font-bold tracking-[-0.03em]">
        {{
          step === 'preview'
            ? t('wallet.topUp.previewTitle')
            : step === 'payment'
              ? t('wallet.topUp.paymentTitle')
              : t('wallet.topUp.title')
        }}
      </h1>
      <span
        class="size-8"
        aria-hidden="true"
      />
    </header>

    <section
      v-if="step === 'form'"
      class="mx-auto flex min-h-[calc(100dvh-4.5rem)] max-w-[390px] flex-col gap-4 px-4 py-4"
      aria-labelledby="top-up-form-title"
    >
      <h2
        id="top-up-form-title"
        class="sr-only"
      >
        {{ t('wallet.topUp.title') }}
      </h2>

      <section class="rounded-[22px] bg-[#1d1d1b] p-4">
        <p class="text-xs text-[#aaa8a3]">{{ t('wallet.topUp.amountLabel') }}</p>
        <label class="mt-2 flex items-center border-b border-[#30302e] pb-3">
          <span class="text-2xl font-extrabold">₩</span>
          <input
            v-model.number="amount"
            type="number"
            min="0"
            inputmode="numeric"
            class="min-w-0 flex-1 bg-transparent px-1 text-3xl font-extrabold leading-none text-[#f5f4f0] outline-none placeholder:text-[#686865]"
            :aria-label="t('wallet.topUp.amountLabel')"
            placeholder="0"
          />
        </label>

        <div class="mt-3 grid grid-cols-2 gap-2">
          <button
            v-for="quickAmount in QUICK_TOPUP_AMOUNTS"
            :key="quickAmount"
            type="button"
            class="rounded-lg border border-[#353533] py-2 text-sm text-[#c7c5c0] transition-colors hover:border-[#f5f4f0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
            @click="setAmount(quickAmount)"
          >
            +{{ formatKrw(quickAmount) }}
          </button>
        </div>
      </section>

      <section class="rounded-[22px] bg-[#1d1d1b] p-4">
        <h2 class="text-xs font-medium text-[#aaa8a3]">{{ t('wallet.topUp.paymentMethod') }}</h2>

        <p
          v-if="methodsQuery.isPending.value"
          class="mt-4 text-sm text-[#aaa8a3]"
        >
          {{ t('wallet.topUp.loadingMethods') }}
        </p>
        <p
          v-else-if="methodsQuery.isError.value"
          class="mt-4 text-sm text-[#ffaaa4]"
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
            class="w-full rounded-xl border px-3 py-3 text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
            :class="
              selectedMethod === method.type ? 'border-[#f5f4f0] bg-[#292927]' : 'border-[#353533]'
            "
            :aria-pressed="selectedMethod === method.type"
            @click="selectedMethod = method.type"
          >
            <span class="flex items-center gap-3 text-sm font-semibold">
              <img
                :src="getTopupMethodLogo(method.type)"
                alt=""
                class="size-8 shrink-0 rounded-lg"
              />
              <span>
                {{ getTopupMethodLabel(method) }}
                <span
                  v-if="method.testMode"
                  class="ml-1 rounded bg-[#f5f4f0] px-1.5 py-0.5 text-[10px] font-bold text-[#242422]"
                >
                  Test
                </span>
              </span>
            </span>
            <span class="mt-1 block text-xs text-[#aaa8a3]">
              {{ t('wallet.topUp.cardDescription') }}
            </span>
          </button>

          <div class="rounded-xl border border-[#292927] px-3 py-3 text-left opacity-35">
            <span class="flex items-center gap-3 text-sm font-semibold">
              <img
                :src="getTopupMethodLogo('PAYPAL')"
                alt=""
                class="size-8 shrink-0 rounded-lg"
              />
              <span>
                PayPal
                <span class="ml-1 rounded bg-[#353533] px-1.5 py-0.5 text-[10px]">Coming soon</span>
              </span>
            </span>
            <span class="mt-1 block text-xs">{{ t('wallet.topUp.paypalDescription') }}</span>
          </div>
        </div>

        <p class="mt-3 rounded-lg bg-[#292927] px-2.5 py-2 text-[11px] text-[#aaa8a3]">
          {{ t('wallet.topUp.sandboxNotice') }}
        </p>
      </section>

      <p
        v-if="previewMutation.isError.value"
        class="rounded-lg bg-[#3b2422] px-3 py-2 text-xs text-[#ffaaa4]"
        role="alert"
      >
        {{ t('wallet.topUp.previewError') }}
      </p>

      <button
        type="button"
        class="mt-auto min-h-13 rounded-xl bg-[#f2f0ea] px-4 text-sm font-bold text-[#172033] transition-opacity focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb] disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="!canContinue"
        @click="submitPreview"
      >
        {{
          previewMutation.isPending.value
            ? t('wallet.topUp.loadingPreview')
            : t('wallet.topUp.next')
        }}
      </button>
    </section>

    <section
      v-else-if="step === 'preview' && preview"
      class="mx-auto flex min-h-[calc(100dvh-4.5rem)] max-w-[390px] flex-col px-4 py-4"
      aria-labelledby="top-up-preview-title"
    >
      <article class="rounded-[22px] bg-[#1d1d1b] p-4">
        <h2
          id="top-up-preview-title"
          class="text-sm font-bold"
        >
          {{ t('wallet.topUp.previewHeading') }}
        </h2>

        <dl class="mt-4 divide-y divide-[#30302e] text-sm">
          <div class="flex items-center justify-between py-3 first:pt-0">
            <dt class="text-[#aaa8a3]">{{ t('wallet.topUp.amountLabel') }}</dt>
            <dd class="font-semibold">{{ formatKrw(preview.amount) }}</dd>
          </div>
          <div class="flex items-center justify-between py-3">
            <dt class="text-[#aaa8a3]">{{ t('wallet.topUp.fee') }}</dt>
            <dd class="font-semibold">{{ formatKrw(preview.fee) }}</dd>
          </div>
          <div class="flex items-center justify-between py-3">
            <dt class="text-[#aaa8a3]">{{ t('wallet.topUp.paymentMethod') }}</dt>
            <dd class="font-semibold">{{ selectedMethodLabel }} (Test Mode)</dd>
          </div>
          <div class="flex items-center justify-between pb-0 pt-3">
            <dt class="text-[#aaa8a3]">{{ t('wallet.topUp.balanceAfter') }}</dt>
            <dd class="font-semibold">{{ formatKrw(preview.expectedSandboxBalance) }}</dd>
          </div>
        </dl>
      </article>

      <p
        v-if="preview.warning"
        class="mt-3 rounded-lg bg-[#3b2422] px-3 py-2 text-xs text-[#ffaaa4]"
      >
        {{ t('wallet.topUp.walletWarning') }}
      </p>

      <p
        v-if="stripeIntentMutation.isError.value"
        class="mt-3 rounded-lg bg-[#3b2422] px-3 py-2 text-xs text-[#ffaaa4]"
        role="alert"
      >
        {{ t('wallet.topUp.paymentRequestError') }}
      </p>

      <div class="mt-auto grid grid-cols-2 gap-3">
        <button
          type="button"
          class="min-h-13 rounded-xl border border-[#5e5e5b] px-4 text-sm font-semibold text-[#f5f4f0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
          @click="step = 'form'"
        >
          {{ t('wallet.topUp.previous') }}
        </button>
        <button
          type="button"
          class="min-h-13 rounded-xl bg-[#f2f0ea] px-4 text-sm font-bold text-[#172033] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
          :disabled="stripeIntentMutation.isPending.value"
          @click="executeTopup"
        >
          {{
            stripeIntentMutation.isPending.value
              ? t('wallet.topUp.loadingPaymentRequest')
              : t('wallet.topUp.execute')
          }}
        </button>
      </div>
    </section>

    <section
      v-else-if="step === 'payment' && stripeIntent"
      class="mx-auto max-w-[390px] px-4 py-4"
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
      class="mx-auto flex min-h-[calc(100dvh-4.5rem)] max-w-[390px] flex-col items-center justify-center px-4 py-8 text-center"
      aria-labelledby="top-up-complete-title"
      aria-live="polite"
    >
      <div
        class="grid size-16 place-items-center rounded-full border-2 border-[#4c4c49] text-3xl text-[#f5f4f0]"
        aria-hidden="true"
      >
        ✓
      </div>
      <h2
        id="top-up-complete-title"
        class="mt-5 text-xl font-bold"
      >
        {{ t('wallet.topUp.completeTitle') }}
      </h2>
      <p class="mt-3 text-3xl font-extrabold text-[#47c887]">
        +{{ formatKrw(preview?.amount ?? 0) }}
      </p>
      <p class="mt-2 text-sm text-[#aaa8a3]">
        {{ t('wallet.topUp.currentBalance', { balance: formatKrw(completedBalance) }) }}
      </p>

      <button
        type="button"
        class="mt-auto min-h-13 w-full rounded-xl bg-[#f2f0ea] px-4 text-sm font-bold text-[#172033] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
        @click="goBack"
      >
        {{ t('wallet.topUp.backToWallet') }}
      </button>
    </section>
  </main>
</template>
