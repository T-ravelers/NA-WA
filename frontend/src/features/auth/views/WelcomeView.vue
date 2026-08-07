<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { SIGN_IN_PATH } from '@/shared/config/routePaths'
import BrandWordmark from '@/shared/ui/BrandWordmark.vue'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <!--
    좌표는 시안 A1 · Get started(1965:1457) 실측이다. 헤딩 블록이 상단 148px에서
    시작하고, 블록과 보딩패스 사이가 92px다.
  -->
  <section class="relative flex min-h-dvh flex-col px-screen pt-[148px] pb-8">
    <div class="flex flex-1 flex-col gap-[92px]">
      <div class="flex flex-col gap-3.5">
        <div class="flex flex-col items-start gap-[33px]">
          <!-- 워드마크는 도형이라 번역 대상이 아니다. 문장 속 표기는 `app.name`이다. -->
          <BrandWordmark class="text-ink-display" />

          <!-- 줄바꿈은 문구에 들어 있다. 로케일마다 끊는 위치가 달라질 수 있다. -->
          <h1
            class="font-display text-[40px] leading-[1.274] font-extralight whitespace-pre-line text-ink-display uppercase"
          >
            {{ t('auth.welcome.headline') }}
          </h1>
        </div>

        <p class="max-w-[290px] text-body text-ink-2">{{ t('auth.welcome.body') }}</p>
      </div>

      <!--
        보딩패스 장식. 이 화면에서만 쓰이므로 컴포넌트로 빼지 않는다.
        절취선 양끝의 원은 카드 바깥으로 물려 티켓의 노치를 만든다.
      -->
      <div
        aria-hidden="true"
        class="relative flex h-[71px] -rotate-2 items-stretch rounded-md bg-food"
      >
        <div class="flex flex-1 flex-col gap-0.5 px-4 py-3.5">
          <span class="font-display text-caption tracking-wide text-on-category/65 uppercase">
            {{ t('auth.welcome.passLabel') }}
          </span>
          <span class="font-display text-section-header font-extrabold text-on-category uppercase">
            {{ t('auth.welcome.passTitle') }}
          </span>
        </div>

        <div
          class="flex w-[66px] shrink-0 items-center justify-center border-l-2 border-dashed border-on-category/35"
        >
          <span
            class="flex size-11 -rotate-8 items-center justify-center rounded-pill border-2 border-on-category/75 font-display text-[8px] tracking-[0.5px] text-on-category opacity-70"
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
