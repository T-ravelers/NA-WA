<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { vFitTextGroup } from '@/shared/lib/fitText'
import {
  buildGoogleMapsSearchUrl,
  buildGoogleMapsTransitRouteUrl,
  buildNaverMapPlaceUrl,
  buildNaverMapTransitRouteUrl,
  hasMapCoordinates,
  openMapAppUrl,
  openMapWebUrl,
  type MapCoordinate,
} from '@/shared/lib/mapLink'
import AppButton from '@/shared/ui/AppButton.vue'

/**
 * 지도 앱으로 나가는 버튼 네 개.
 *
 * Event 상세와 Place 상세가 같은 버튼 묶음을 쓴다. 두 화면에 복붙해 두었더니 한쪽만
 * 고쳐지는 일이 실제로 일어나, URL 단언이 한 화면의 spec에만 붙어 있었다(#345 → #351).
 * URL 조립 규칙 자체는 `shared/lib/mapLink.ts`가 소유하고 여기서는 부르기만 한다.
 *
 * 좌표가 없는 항목은 묶음 전체를 렌더링하지 않는다(#221 계약).
 */
interface Props {
  latitude: MapCoordinate
  longitude: MapCoordinate
  /** 네이버 지도 앱이 모든 진입 URL에 필수로 요구하는 장소명. */
  name: string
}

const { latitude, longitude, name } = defineProps<Props>()

const { t } = useI18n()

const hasCoordinates = computed(() => hasMapCoordinates(latitude, longitude))

const googleSearchUrl = computed(() => buildGoogleMapsSearchUrl(latitude, longitude))
const googleTransitRouteUrl = computed(() => buildGoogleMapsTransitRouteUrl(latitude, longitude))
const naverPlaceUrl = computed(() => buildNaverMapPlaceUrl(latitude, longitude, name))
const naverRouteUrl = computed(() => buildNaverMapTransitRouteUrl(latitude, longitude, name))
</script>

<template>
  <div
    v-if="hasCoordinates"
    v-fit-text-group
    class="grid min-w-0 grid-cols-2 gap-2"
  >
    <div class="min-w-0">
      <AppButton
        block
        compact
        variant="secondary"
        @click="openMapWebUrl(googleSearchUrl)"
      >
        {{ t('explore.mapLinks.openInGoogleMaps') }}
      </AppButton>
    </div>
    <div class="min-w-0">
      <AppButton
        block
        compact
        variant="secondary"
        @click="openMapWebUrl(googleTransitRouteUrl)"
      >
        {{ t('explore.mapLinks.googleTransit') }}
      </AppButton>
    </div>
    <div class="min-w-0">
      <AppButton
        block
        compact
        variant="secondary"
        @click="openMapAppUrl(naverPlaceUrl)"
      >
        {{ t('explore.mapLinks.openInNaverMap') }}
      </AppButton>
    </div>
    <div class="min-w-0">
      <AppButton
        block
        compact
        variant="secondary"
        @click="openMapAppUrl(naverRouteUrl)"
      >
        {{ t('explore.mapLinks.naverTransit') }}
      </AppButton>
    </div>
  </div>
</template>
