import type { QueryClient } from '@tanstack/vue-query'

import { notificationKeys } from '@/features/notification/model/notificationQueries'
import { isPushReceivedMessage } from '@/features/notification/model/pushNotification'
import { settlementKeys } from '@/features/settlement/model/settlementQueries'

/**
 * 서비스 워커가 푸시를 받았다고 알리면 알림·정산 쿼리를 낡은 것으로 표시한다.
 *
 * 열린 창은 15초마다 벨 숫자를 물어보지만, 푸시가 도착한 순간은 이미 바뀐 것을 아는 순간이다.
 * 기다리지 않고 바로 다시 받아 온다. 푸시는 목록을 대신하지 않으므로 여기서 알림을 읽음으로
 * 바꾸거나 화면을 옮기지는 않는다.
 *
 * 알림 feature와 정산 feature가 서로를 모르도록 두 키를 함께 아는 이 자리(app)에서 잇는다.
 */
export function listenForPushMessages(
  queryClient: QueryClient,
  container: Pick<ServiceWorkerContainer, 'addEventListener'> | undefined,
): void {
  container?.addEventListener('message', (event) => {
    if (!isPushReceivedMessage(event.data)) return

    void queryClient.invalidateQueries({ queryKey: notificationKeys.all })
    void queryClient.invalidateQueries({ queryKey: settlementKeys.all })
  })
}
