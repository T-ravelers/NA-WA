import { useQuery } from '@tanstack/vue-query'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { fetchPushPublicKey } from '../api/pushSubscriptionApi'
import {
  browserPushEnvironment,
  detectPushSupport,
  disablePush,
  enablePush,
  readPushSubscription,
} from './pushSubscription'

/**
 * 카드가 어떤 모습인가.
 *
 * - `hidden`: 보여 줄 것이 없다(서버 푸시가 꺼짐·브라우저 미지원·아직 읽는 중).
 * - `install`: iOS 사파리 탭이다. 홈 화면에 추가하라고 안내한다.
 * - `blocked`: 브라우저에서 알림이 막혀 있다. 앱이 풀 수 없으니 안내만 한다.
 * - `off` / `on`: 이 기기에서 푸시를 켤 수 있다 / 켜져 있다.
 */
export type PushCardMode = 'hidden' | 'install' | 'blocked' | 'off' | 'on'

/**
 * 이 기기의 푸시 구독을 켜고 끄는 화면 상태.
 *
 * 어떤 단계가 막히거나 실패해도 알림 목록은 그대로 동작해야 하므로, 이 상태는 카드 하나만
 * 좌우한다. 지원하지 않는 브라우저에서는 서버에 아무것도 묻지 않는다.
 */
export function usePushSubscription() {
  const { locale } = useI18n()

  const support = detectPushSupport(browserPushEnvironment())
  const permission = ref<NotificationPermission>(
    support === 'supported' ? Notification.permission : 'default',
  )
  const subscribed = ref(false)
  // 이 기기의 구독을 읽기 전에 카드를 그리면 "켜기"가 잠깐 비쳤다가 "끄기"로 바뀐다.
  const checked = ref(support !== 'supported')
  const busy = ref(false)
  const failed = ref(false)

  /*
   * 공개키는 클릭하기 전에 받아 둔다. 권한 요청은 클릭에서 곧바로 이어져야 하고, 그 사이에 서버
   * 응답을 기다리면 Safari가 사용자 동작으로 보지 않는다. 키가 비어 있으면 서버의 푸시가
   * 꺼진 것이다.
   */
  const publicKey = useQuery({
    queryKey: ['push', 'public-key'],
    queryFn: fetchPushPublicKey,
    enabled: support !== 'unsupported',
    staleTime: Infinity,
    retry: false,
  })

  onMounted(async () => {
    if (support === 'supported') {
      try {
        subscribed.value = (await readPushSubscription()) !== null
      } catch {
        subscribed.value = false
      }
      permission.value = Notification.permission
    }
    checked.value = true
  })

  const mode = computed<PushCardMode>(() => {
    if (support === 'unsupported' || !checked.value) return 'hidden'
    if (typeof publicKey.data.value !== 'string') return 'hidden'
    if (support === 'needs-install') return 'install'
    if (permission.value === 'denied') return 'blocked'

    return subscribed.value && permission.value === 'granted' ? 'on' : 'off'
  })

  async function enable(): Promise<void> {
    const key = publicKey.data.value
    if (busy.value || typeof key !== 'string') return

    busy.value = true
    failed.value = false
    try {
      const result = await enablePush(key, locale.value)
      permission.value = result.permission
      subscribed.value = result.enabled
    } catch {
      failed.value = true
    } finally {
      busy.value = false
    }
  }

  async function disable(): Promise<void> {
    if (busy.value) return

    busy.value = true
    failed.value = false
    try {
      await disablePush()
      subscribed.value = false
    } catch {
      failed.value = true
    } finally {
      busy.value = false
    }
  }

  return { mode, busy, failed, enable, disable }
}
