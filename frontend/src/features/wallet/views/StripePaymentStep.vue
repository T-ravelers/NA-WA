<script setup lang="ts">
import {
  loadStripe,
  type Stripe,
  type StripeElements,
  type StripePaymentElement,
} from '@stripe/stripe-js'
import { ref, onBeforeUnmount, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

import { getStripeTopupStatus } from '../api/topupApi'
import { formatKrw, type StripeTopupStatusResponse } from '../model/topup'

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
          colorPrimary: '#91cdbb',
          colorBackground: '#1d1d1b',
          colorText: '#f5f4f0',
          colorDanger: '#ffaaa4',
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
    class="flex min-h-[calc(100dvh-4.5rem)] flex-col gap-4"
    @submit.prevent="submitPayment"
  >
    <section class="rounded-[22px] bg-[#1d1d1b] p-4">
      <div
        ref="paymentElementContainer"
        class="min-h-48"
      >
        <p
          v-if="isLoading"
          class="rounded-xl border border-[#30302e] px-4 py-8 text-center text-sm text-[#aaa8a3]"
          role="status"
        >
          {{ t('wallet.topUp.paymentLoading') }}
        </p>
      </div>
    </section>

    <p
      v-if="errorMessage"
      class="rounded-lg bg-[#3b2422] px-3 py-2 text-sm text-[#ffaaa4]"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <div class="mt-auto grid grid-cols-2 gap-3">
      <button
        type="button"
        class="min-h-13 rounded-xl border border-[#5e5e5b] px-4 text-sm font-semibold text-[#f5f4f0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb]"
        @click="emit('back')"
      >
        {{ t('wallet.topUp.previous') }}
      </button>
      <button
        type="submit"
        class="min-h-13 rounded-xl bg-[#f2f0ea] px-4 text-sm font-bold text-[#172033] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#91cdbb] disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="isSubmitting"
      >
        {{
          isSubmitting
            ? t('wallet.topUp.paymentSubmitting')
            : t('wallet.topUp.paymentSubmit', { amount: formatKrw(amount) })
        }}
      </button>
    </div>
  </form>
</template>
