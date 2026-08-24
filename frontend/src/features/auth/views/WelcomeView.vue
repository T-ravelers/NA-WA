<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { MERCHANT_HOME_PATH, SIGN_IN_PATH } from '@/shared/config/routePaths'
import { vFitText } from '@/shared/lib/fitText'
import BrandWordmark from '@/shared/ui/BrandWordmark.vue'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <!--
    좌표는 시안 A1 · Get started(1965:1457) 실측이다. 헤딩 블록이 상단 148px에서
    시작하고, 390×844에서 블록과 보딩패스 사이가 92px다.

    667px은 가장 긴 vi 고정 콘텐츠와 회전한 티켓의 안전 여백을 합친 높이다. 390px 이상에서는
    남은 화면 높이를 간격으로 쓰되 32~92px로 제한한다. 그래서 주소창 높이가 바뀌어도 간격이
    한 번에 뛰지 않는다. 360px 미만은 줄바꿈이 많으므로 항상 32px이다.
  -->
  <section class="relative flex min-h-dvh flex-col px-screen pt-welcome-top pb-8">
    <div
      class="min-[360px]:gap-[clamp(2rem,calc(100dvh-667px),var(--spacing-welcome-ticket-gap))] flex flex-1 flex-col gap-8"
    >
      <div class="flex flex-col gap-3.5">
        <div class="flex flex-col items-start gap-welcome-wordmark-gap">
          <!-- 워드마크는 도형이라 번역 대상이 아니다. 문장 속 표기는 `app.name`이다. -->
          <BrandWordmark class="text-ink-display" />

          <!-- 줄바꿈은 문구에 들어 있다. 로케일마다 끊는 위치가 달라질 수 있다. -->
          <h1
            class="font-display text-welcome-headline whitespace-pre-line text-ink-display uppercase"
          >
            {{ t('auth.welcome.headline') }}
          </h1>
        </div>

        <p class="max-w-welcome-copy-width text-body text-ink-2">{{ t('auth.welcome.body') }}</p>
      </div>

      <!--
        보딩패스 장식. 이 화면에서만 쓰이므로 컴포넌트로 빼지 않는다.
        절취선 양끝의 원은 카드 바깥으로 물려 티켓의 노치를 만든다.
      -->
      <div
        aria-hidden="true"
        class="h-welcome-ticket-height relative mb-2 flex -rotate-2 items-stretch rounded-md bg-food"
      >
        <!--
          `min-w-0`이 없으면 이 칸이 글자 길이만큼 벌어져 옆의 스텁을 화면 밖으로 밀어낸다.
          280px에서 절취선과 스탬프가 통째로 사라지던 원인이다.
        -->
        <div class="flex min-w-0 flex-1 flex-col gap-0.5 px-4 py-3.5">
          <!--
            라벨도 같이 줄인다. 280에서 칸이 142px인데 이 문구는 한 줄에 143.34px가 필요해
            1.34px 차이로 두 줄이 되고, 그러면 라벨(12px)이 줄어든 제목(11.93px)보다 커져
            위계가 뒤집힌다. 1.3%만 줄면 한 줄로 돌아온다.

            `v-fit-text-group`은 쓰지 않는다. 묶음은 같은 비율을 공유하므로 라벨이 제목의
            비율(0.54배)을 따라가 6.5px까지 내려간다.
          -->
          <span
            v-fit-text
            class="font-display text-caption tracking-wide truncate text-on-category/65 uppercase"
          >
            {{ t('auth.welcome.passLabel') }}
          </span>
          <!--
            시안은 ExtraBold(800)이지만 Sztos Variable의 wght 축이 700에서 끝난다.
            시안에서도 이 제목은 티켓 폭을 꽉 채우는 한 줄이다(텍스트 257px / 프레임 250px).

            줄을 꺾으면 고정 높이 티켓 밖으로 흘러넘치므로 한 줄은 그대로 지키되, 잘라내지 않고
            글자를 줄인다(#326 결정 11).

            **390에서도 줄어든다.** 실측하면 이 문구가 262px인데 칸은 252px이라, 한 줄로
            고정해 두면 390에서도 티켓이 9px 넘쳤다(`scrollWidth 359 / clientWidth 350`).
            노치 원이 절취선에서 떨어져 나와 있던 것이 그 결과다.
          -->
          <span
            v-fit-text
            class="font-display text-section-header truncate text-on-category uppercase"
          >
            {{ t('auth.welcome.passTitle') }}
          </span>
        </div>

        <div
          class="w-welcome-ticket-stub-width flex shrink-0 items-center justify-center border-l-2 border-dashed border-on-category/35"
        >
          <span
            class="flex size-11 -rotate-8 items-center justify-center rounded-pill border-2 border-on-category/75 font-display text-welcome-stamp text-on-category opacity-70"
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

    <!--
      가맹점 진입점. 손님이 절대다수라 기본 흐름을 한 단계도 늘리지 않고 링크로만 둔다.
      로그인 여부는 확인하지 않는다. 미인증이면 guard가 복귀 경로를 붙여 로그인으로 보낸다.
    -->
    <RouterLink
      :to="MERCHANT_HOME_PATH"
      class="mt-4 self-center text-body-sm text-ink-3 underline underline-offset-4"
    >
      {{ t('auth.welcome.merchantEntry') }}
    </RouterLink>
  </section>
</template>
