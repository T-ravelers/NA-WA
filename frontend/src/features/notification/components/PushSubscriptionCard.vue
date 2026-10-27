<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import { usePushSubscription } from '../model/usePushSubscription'

/**
 * 이 기기로 푸시 알림을 받을지 정하는 카드.
 *
 * 알림 목록 맨 위에 둔다. 알림을 보고 있는 순간이 "앱을 닫아도 받고 싶다"를 가장 자연스럽게
 * 묻는 때이고, 권한 요청은 사용자의 클릭에서 시작해야 하기 때문이다.
 *
 * 켜지 않아도, 막혀 있어도, 브라우저가 지원하지 않아도 아래 알림 목록은 그대로 동작한다.
 * 보여 줄 것이 없으면 카드 자체가 사라진다.
 */
const { t } = useI18n()
const { mode, busy, failed, enable, disable } = usePushSubscription()
</script>

<template>
  <AppCard
    v-if="mode !== 'hidden'"
    class="mt-4"
    data-testid="push-card"
  >
    <h2 class="text-body-sm font-semibold text-ink">{{ t('notification.push.title') }}</h2>
    <p class="mt-1 text-body-sm text-ink-2">{{ t(`notification.push.${mode}.description`) }}</p>

    <AppButton
      v-if="mode === 'off'"
      class="mt-3"
      block
      variant="secondary"
      :loading="busy"
      data-testid="push-enable"
      @click="enable"
    >
      {{ t('notification.push.off.action') }}
    </AppButton>
    <AppButton
      v-else-if="mode === 'on'"
      class="mt-3"
      block
      variant="tertiary"
      :loading="busy"
      data-testid="push-disable"
      @click="disable"
    >
      {{ t('notification.push.on.action') }}
    </AppButton>

    <p
      v-if="failed"
      role="alert"
      class="mt-2 text-caption text-ink-3"
      data-testid="push-failed"
    >
      {{ t('error.unknown') }}
    </p>
  </AppCard>
</template>
