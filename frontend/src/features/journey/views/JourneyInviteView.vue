<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { IconArrowLeft, IconLink, IconQrcode } from '@tabler/icons-vue'
import { Motion } from 'motion-v'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'

import { formatCalendarDateString } from '@/shared/lib/datetime'
import { vFitText, vFitTextGroup } from '@/shared/lib/fitText'
import AppButton from '@/shared/ui/AppButton.vue'
import AppTicket from '@/shared/ui/AppTicket.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import { showToast } from '@/shared/ui/toast'

import JourneyInviteQrSheet from '../components/JourneyInviteQrSheet.vue'
import { buildJourneyInviteCode, buildJourneyInviteUrl } from '../model/inviteCode'
import { isJourneyForbidden, journeyErrorMessageKey } from '../model/journeyErrors'
import { journeyDetailQueryOptions } from '../model/journeyQueries'

/**
 * 초대하기 — 여정을 탑승권으로 보여주고 링크·QR로 건넨다.
 *
 * 시안 `2430:4648`. 티켓 조형은 `AppTicket`이 소유하므로 여기서 다시 만들지 않는다.
 *
 * **시안의 참여자 목록(`TRAVELERS · 3 of 4`)은 여기에 없다.** 여정은 단독 소유이고
 * 백엔드에 멤버 조회 경로가 없다. 없는 사람을 지어내면 눌러 볼 수 없는 목록이 남고
 * 진짜 계약이 오면 통째로 버려야 한다(#438 제외 범위).
 *
 * 초대 코드도 같은 이유로 **표시 전용**이다. 실제로 동작하는 초대 경로는 링크와 QR이고,
 * 둘은 같은 여정 주소를 가리킨다.
 */
const i18n = useI18n()
const { t } = i18n
const route = useRoute()

const tripId = computed(() => {
  const raw = Array.isArray(route.params.tripId) ? route.params.tripId[0] : route.params.tripId
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
})

const detailQuery = useQuery({
  ...journeyDetailQueryOptions(tripId),
  enabled: computed(() => tripId.value !== null),
  retry: false,
})

const forbidden = computed(() => isJourneyForbidden(detailQuery.error.value))

const journey = computed(() => detailQuery.data.value ?? null)

const inviteCode = computed(() =>
  tripId.value === null ? '' : buildJourneyInviteCode(tripId.value),
)

/* 링크는 지금 열려 있는 주소를 기준으로 만든다. 배포 환경마다 origin이 다르다. */
const inviteUrl = computed(() =>
  tripId.value === null
    ? ''
    : buildJourneyInviteUrl(tripId.value, globalThis.location?.origin ?? ''),
)

const regionLabel = computed(() =>
  (journey.value?.regions ?? [])
    .slice()
    .sort((a, b) => a.displayOrder - b.displayOrder)
    .map((region) => region.regionName)
    .join(' · '),
)

const periodLabel = computed(() => {
  const current = journey.value
  if (current === null) return ''

  const start = formatCalendarDateString(current.startDate)
  const end = formatCalendarDateString(current.endDate)

  return start === '' || end === '' ? '' : `${start} – ${end}`
})

/** 티켓 body의 눈썹 문구. 지역이 없는 여정도 있어 그때는 라벨만 남긴다. */
const boardingLabel = computed(() =>
  regionLabel.value === ''
    ? t('journey.invite.boardingPass')
    : `${t('journey.invite.boardingPass')} · ${regionLabel.value}`,
)

const qrSheetOpen = ref(false)

async function copyInviteLink(): Promise<void> {
  /* 공유 시트가 있으면 그쪽이 낫다. 어디로 보낼지 사용자가 고를 수 있다. */
  try {
    if (navigator.share) {
      await navigator.share({ title: journey.value?.title, url: inviteUrl.value })
      return
    }

    if (navigator.clipboard) {
      await navigator.clipboard.writeText(inviteUrl.value)
      showToast(t('journey.invite.linkCopied'))
      return
    }

    showToast(t('journey.invite.linkUnavailable'))
  } catch {
    /* 공유 시트는 아무것도 고르지 않고 닫을 수 있다. 그것은 실패가 아니다. */
  }
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col gap-5 px-screen py-8">
    <StateLoading
      v-if="detailQuery.isPending.value"
      :label="t('state.loading')"
    />

    <section
      v-else-if="forbidden"
      role="alert"
    >
      <StateEmpty
        :title="t('journey.detail.accessDeniedTitle')"
        :description="t('journey.detail.accessDeniedDescription')"
      />
    </section>

    <StateError
      v-else-if="detailQuery.isError.value"
      :title="t('journey.invite.loadFailed')"
      :description="t(journeyErrorMessageKey(detailQuery.error.value, (key) => i18n.te(key)))"
      :action-label="t('action.retry')"
      @retry="detailQuery.refetch"
    />

    <template v-else-if="journey !== null">
      <header class="flex items-center gap-0.5">
        <RouterLink
          :to="{ name: 'journey-detail', params: { tripId: journey.tripId } }"
          :aria-label="t('action.back')"
          class="-ml-3 flex size-11 shrink-0 items-center justify-center text-ink"
        >
          <IconArrowLeft
            :size="24"
            :stroke-width="1.75"
            aria-hidden="true"
          />
        </RouterLink>
        <h1
          v-fit-text
          class="min-w-0 flex-1 truncate font-display text-screen-title uppercase text-ink-display"
        >
          {{ t('journey.invite.title') }}
        </h1>
      </header>

      <!--
        신규 화면 두 장에만 motion-v를 쓴다(#326 결정 10). 티켓이 건네받는 물건처럼
        아래에서 올라온다. `prefers-reduced-motion`은 motion-v가 알아서 존중한다.
      -->
      <Motion
        as="div"
        :initial="{ opacity: 0, y: 16 }"
        :animate="{ opacity: 1, y: 0 }"
        :transition="{ duration: 0.32, ease: [0.22, 1, 0.36, 1] }"
      >
        <AppTicket
          :body-size="108"
          tone="paper"
        >
          <template #body>
            <div class="flex flex-col gap-1.5 p-5">
              <!--
                지역이 많으면 눈썹이 두 줄이 되고, 그만큼 body가 길어져 절취선이 날짜
                위로 올라온다. 절취선 위치는 고정값이라 내용이 넘치면 조형이 깨진다.
              -->
              <p class="truncate text-caption uppercase tracking-[0.05em] text-on-paper/70">
                {{ boardingLabel }}
              </p>
              <h2
                v-fit-text
                class="min-w-0 truncate font-display text-section-header uppercase text-on-paper"
              >
                {{ journey.title }}
              </h2>
              <p
                v-if="periodLabel !== ''"
                class="text-caption text-on-paper/60"
              >
                {{ periodLabel }}
              </p>
            </div>
          </template>

          <template #stub>
            <div class="flex items-center justify-between px-5 py-4">
              <p class="text-title-sm tracking-[0.18em] text-on-paper">{{ inviteCode }}</p>
              <p class="text-caption uppercase text-on-paper/70">
                {{ t('journey.invite.inviteCode') }}
              </p>
            </div>
          </template>
        </AppTicket>
      </Motion>

      <div
        v-fit-text-group
        class="grid min-w-0 grid-cols-2 gap-2"
      >
        <div class="min-w-0">
          <AppButton
            block
            compact
            variant="secondary"
            @click="copyInviteLink"
          >
            <span class="inline-flex items-center gap-2">
              <IconLink
                :size="20"
                :stroke-width="1.75"
                aria-hidden="true"
              />
              {{ t('journey.invite.copyLink') }}
            </span>
          </AppButton>
        </div>
        <div class="min-w-0">
          <AppButton
            block
            compact
            variant="secondary"
            @click="qrSheetOpen = true"
          >
            <span class="inline-flex items-center gap-2">
              <IconQrcode
                :size="20"
                :stroke-width="1.75"
                aria-hidden="true"
              />
              {{ t('journey.invite.showQr') }}
            </span>
          </AppButton>
        </div>
      </div>

      <p class="text-caption text-ink-3">{{ t('journey.invite.hint') }}</p>

      <AppButton
        block
        class="mt-auto"
        @click="$router.push({ name: 'journey-detail', params: { tripId: journey.tripId } })"
      >
        {{ t('journey.invite.done') }}
      </AppButton>

      <JourneyInviteQrSheet
        v-if="qrSheetOpen"
        :url="inviteUrl"
        :code="inviteCode"
        :title="t('journey.invite.qrTitle')"
        :image-label="t('journey.invite.qrImageLabel')"
        :failed-label="t('journey.invite.qrFailed')"
        :close-label="t('action.close')"
        @close="qrSheetOpen = false"
      />
    </template>
  </main>
</template>
