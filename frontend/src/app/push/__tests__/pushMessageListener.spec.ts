import { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'

import { notificationKeys } from '@/features/notification/model/notificationQueries'
import { PUSH_RECEIVED_MESSAGE_TYPE } from '@/features/notification/model/pushNotification'
import { settlementKeys } from '@/features/settlement/model/settlementQueries'

import { listenForPushMessages } from '../pushMessageListener'

function setUp() {
  const queryClient = new QueryClient()
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue()
  let handler: ((event: MessageEvent) => void) | undefined
  const container = {
    addEventListener: vi.fn((_type: string, listener: (event: MessageEvent) => void) => {
      handler = listener
    }),
  }
  listenForPushMessages(queryClient, container as unknown as ServiceWorkerContainer)

  return {
    invalidate,
    container,
    receive: (data: unknown) => handler?.({ data } as MessageEvent),
  }
}

describe('listenForPushMessages', () => {
  it('refetches notifications and settlements as soon as a push arrives', () => {
    const { invalidate, container, receive } = setUp()

    receive({ type: PUSH_RECEIVED_MESSAGE_TYPE })

    expect(container.addEventListener).toHaveBeenCalledWith('message', expect.any(Function))
    expect(invalidate).toHaveBeenCalledTimes(2)
    expect(invalidate).toHaveBeenCalledWith({ queryKey: notificationKeys.all })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: settlementKeys.all })
  })

  it('ignores messages the push handler did not send', () => {
    const { invalidate, receive } = setUp()

    receive({ type: 'something-else' })
    receive('na-wa:push-received')
    receive(undefined)

    expect(invalidate).not.toHaveBeenCalled()
  })

  it('does nothing where service workers are unavailable', () => {
    expect(() => listenForPushMessages(new QueryClient(), undefined)).not.toThrow()
  })
})
