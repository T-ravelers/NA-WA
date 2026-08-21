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
      // Stripe Payment Element는 브라우저 로케일을 자동 감지("auto")해 카드 입력
      // 필드 라벨을 렌더링한다. 앱은 아직 다국어 결제 흐름을 지원하지 않으므로 영어로 고정한다.
      locale: 'en',
      // Payment Element는 별도 origin의 iframe이라 fontFamily: 'inherit'로는 부모
      // 페이지가 셀프 호스팅한 Noto Sans(frontend/public/fonts/NotoSans.woff2)를
      // 실제로 상속받지 못한다. 같은 파일을 iframe에도 명시적으로 로드시킨다.
      fonts: [
        {
          family: 'Noto Sans',
          src: `url(${window.location.origin}/fonts/NotoSans.woff2) format('woff2-variations')`,
          weight: '100 900',
        },
      ],
      appearance: {
        theme: 'night',
        variables: {
          colorPrimary: readToken('--color-success-subtle', '#8ec4b1'),
          colorBackground: readToken('--color-surface-1', '#262626'),
          colorText: readToken('--color-ink', '#fbfaf8'),
          colorDanger: readToken('--color-danger', '#ed3423'),
          fontFamily: "'Noto Sans', sans-serif",
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

/**
 * Stripe.js의 developer tools 위젯("testing assistant")은 Payment Element가 아니라
 * Stripe() 인스턴스 자체에 붙어서 document.body 바로 아래에 별도로 뜬다. 그래서
 * paymentElement.destroy()로는 안 지워지고, 다른 화면으로 이동해도 계속 떠 있는다.
 *
 * 공식 API로는 생성 시점에 끄거나 켜는 것만 가능하고(loadStripe의
 * developerTools.assistant.enabled), 이미 뜬 위젯을 런타임에 숨기는 API는 없다
 * (https://docs.stripe.com/sdks/stripejs-testing-assistant#hide-the-testing-assistant).
 * 결제 화면에서는 자동완성 등에 쓸모가 있어 켜 둔 채로 쓰고, 이 화면을 벗어날 때만
 * DOM에서 직접 지운다. title 속성은 Stripe SDK 내부 구현이라 버전이 바뀌면 셀렉터가
 * 깨질 수 있다 — 결제 화면을 벗어나도 위젯이 남으면 이 title 문자열부터 다시 확인한다.
 */
const removeDeveloperToolsFrame = (): void => {
  document
    .querySelectorAll<HTMLIFrameElement>('iframe[title="Stripe developer tools frame"]')
    .forEach((frame) => frame.remove())
}

onBeforeUnmount(() => {
  paymentElement.value?.destroy()
  removeDeveloperToolsFrame()
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
            : t('wallet.topUp.paymentSubmit', { amount: formatKrw(amount) })
        }}
      </AppButton>
    </div>
  </form>
</template>
