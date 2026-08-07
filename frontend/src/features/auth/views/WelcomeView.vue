<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { SIGN_IN_PATH } from '@/shared/config/routePaths'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <section class="relative flex min-h-dvh flex-col px-screen pt-14 pb-8">
    <p
      class="text-center font-display text-section-header tracking-[2.5px] text-ink-display uppercase"
    >
      {{ t('app.name') }}
    </p>

    <div class="mt-16 flex flex-1 flex-col gap-7">
      <div class="flex flex-col gap-3.5">
        <!-- 줄바꿈은 문구에 들어 있다. 로케일마다 끊는 위치가 달라질 수 있다. -->
        <h1
          class="font-display text-[52px] leading-[0.98] font-medium whitespace-pre-line text-food uppercase"
        >
          {{ t('auth.welcome.headline') }}
        </h1>
        <p class="max-w-[320px] text-body text-ink-2">{{ t('auth.welcome.body') }}</p>
      </div>

      <!--
        보딩패스 장식. 이 화면에서만 쓰이므로 컴포넌트로 빼지 않는다.
        절취선 양끝의 원은 카드 바깥으로 물려 티켓의 노치를 만든다.
      -->
      <div
        aria-hidden="true"
        class="relative flex items-stretch rounded-md bg-paper"
      >
        <div class="flex flex-1 flex-col gap-0.5 px-4 py-3.5">
          <span class="font-display text-caption tracking-wide text-on-paper/65 uppercase">
            {{ t('auth.welcome.passLabel') }}
          </span>
          <span class="font-display text-trip-ticket-title text-on-paper uppercase">
            {{ t('auth.welcome.passTitle') }}
          </span>
        </div>

        <div
          class="flex w-16 shrink-0 items-center justify-center border-l-2 border-dashed border-on-paper/35"
        >
          <span
            class="flex size-10 -rotate-8 items-center justify-center rounded-pill border-2 border-on-paper/75 font-display text-caption text-on-paper opacity-70"
          >
            {{ t('auth.welcome.passStamp') }}
          </span>
        </div>

        <span class="absolute -top-1.5 right-[58px] size-3 rounded-pill bg-canvas" />
        <span class="absolute -bottom-1.5 right-[58px] size-3 rounded-pill bg-canvas" />
      </div>
    </div>

    <button
      type="button"
      class="h-13 w-full rounded-sm bg-paper-fill text-title-sm text-on-paper"
      @click="router.push(SIGN_IN_PATH)"
    >
      {{ t('auth.welcome.start') }}
    </button>
  </section>
</template>
