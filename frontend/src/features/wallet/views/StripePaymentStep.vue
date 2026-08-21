<script setup lang="ts">
import {
  loadStripe,
  type Stripe,
  type StripeElements,
  type StripePaymentElement,
} from '@stripe/stripe-js'
import { ref, onBeforeUnmount, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import { getStripeTopupStatus } from '../api/topupApi'
import { formatPoints, type StripeTopupStatusResponse } from '../model/topup'

const props = defineProps<{
  clientSecret: string
  topupId: number
  amount: string | number
}>()

const emit = defineEmits<{
  'payment-confirmed': [status: StripeTopupStatusResponse]
  'payment-error': [message: string]
  back: []
}>()

const { t } = useI18n()

const paymentElementContainer = ref<HTMLDivElement | null>(null)
const stripe = ref<Stripe | null>(null)
const elements = ref<StripeElements | null>(null)
const paymentElement = ref<StripePaymentElement | null>(null)
const isLoading = ref(true)
const isSubmitting = ref(false)
const errorMessage = ref('')

const getErrorMessage = (error: unknown, fallback: string): string => {
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = error.message
    if (typeof message === 'string' && message.length > 0) return message
  }

  return fallback
}

/**
 * Stripe iframe에 넘길 색. CSS 변수를 못 읽는 iframe이라 런타임에 계산값을 뽑아 넘긴다.
 * 값을 여기 적어 두면 tokens.css가 바뀔 때마다 어긋나므로 토큰을 단일 정본으로 유지한다.
 */
const readToken = (name: string, fallback: string): string => {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value === '' ? fallback : value
}

const initializePaymentElement = async (): Promise<void> => {
  isLoading.value = true
  errorMessage.value = ''

  const publishableKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY
  if (!publishableKey) {
    errorMessage.value = t('wallet.topUp.paymentLoadError')
    isLoading.value = false
    return
  }

  try {
    const loadedStripe = await loadStripe(publishableKey)
    if (!loadedStripe || !paymentElementContainer.value) {
      throw new Error(t('wallet.topUp.paymentLoadError'))
    }

    stripe.value = loadedStripe
    const paymentElements = loadedStripe.elements({
      clientSecret: props.clientSecret,
      appearance: {
        theme: 'night',
        variables: {
          colorPrimary: readToken('--color-success-subtle', '#8ec4b1'),
          colorBackground: readToken('--color-surface-1', '#262626'),
          colorText: readToken('--color-ink', '#fbfaf8'),
          colorDanger: readToken('--color-danger', '#ed3423'),
          fontFamily: 'inherit',
          borderRadius: '12px',
        },
      },
    })
    elements.value = paymentElements

    paymentElement.value = paymentElements.create('payment', { layout: 'accordion' })
    paymentElement.value.mount(paymentElementContainer.value)
  } catch (error) {
    errorMessage.value = getErrorMessage(error, t('wallet.topUp.paymentLoadError'))
  } finally {
    isLoading.value = false
  }
}

const submitPayment = async (): Promise<void> => {
  if (isSubmitting.value) return

  if (!stripe.value || !elements.value) {
    errorMessage.value = t('wallet.topUp.paymentLoadError')
    return
  }

  isSubmitting.value = true
  errorMessage.value = ''

  try {
    const result = await stripe.value.confirmPayment({
      elements: elements.value,
      redirect: 'if_required',
    })

    if (result.error) {
      errorMessage.value = result.error.message ?? t('wallet.topUp.paymentError')
      emit('payment-error', errorMessage.value)
      return
    }

    const status = await getStripeTopupStatus(props.topupId)
    if (status.status === 'SUCCESS') {
      emit('payment-confirmed', status)
      return
    }

    errorMessage.value =
      status.status === 'PENDING' || status.status === 'READY'
        ? t('wallet.topUp.paymentPending')
        : t('wallet.topUp.paymentError')
    emit('payment-error', errorMessage.value)
  } catch (error) {
    errorMessage.value = getErrorMessage(error, t('wallet.topUp.paymentError'))
    emit('payment-error', errorMessage.value)
  } finally {
    isSubmitting.value = false
  }
}

onMounted(() => {
  void initializePaymentElement()
})

onBeforeUnmount(() => {
  paymentElement.value?.destroy()
})
</script>

<template>
  <form
    class="flex flex-1 flex-col gap-4"
    @submit.prevent="submitPayment"
  >
    <AppCard>
      <div
        ref="paymentElementContainer"
        class="min-h-48"
      >
        <p
          v-if="isLoading"
          class="rounded-sm border border-hairline px-4 py-8 text-center text-body-sm text-ink-2"
          role="status"
        >
          {{ t('wallet.topUp.paymentLoading') }}
        </p>
      </div>
    </AppCard>

    <p
      v-if="errorMessage"
      class="text-body-sm text-danger"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <div class="mt-auto grid grid-cols-2 gap-3">
      <AppButton
        variant="secondary"
        @click="emit('back')"
      >
        {{ t('wallet.topUp.previous') }}
      </AppButton>
      <AppButton
        type="submit"
        :disabled="isSubmitting"
        :loading="isSubmitting"
      >
        {{
          isSubmitting
            ? t('wallet.topUp.paymentSubmitting')
            : t('wallet.topUp.paymentSubmit', { amount: formatPoints(amount) })
        }}
      </AppButton>
    </div>
  </form>
</template>
