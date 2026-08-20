import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'

import { i18n } from '@/app/i18n'

const likeExploreItem = vi.fn()
const unlikeExploreItem = vi.fn()
const showToast = vi.fn()

vi.mock('../../api/exploreApi', () => ({
  likeExploreItem: (itemId: number) => likeExploreItem(itemId),
  unlikeExploreItem: (itemId: number) => unlikeExploreItem(itemId),
}))

vi.mock('@/shared/ui/toast', () => ({
  showToast: (message: string) => showToast(message),
}))

const { useExploreItemLikeMutation } = await import('../useExploreItemLikeMutation')

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  let mutation!: ReturnType<typeof useExploreItemLikeMutation>

  mount(
    defineComponent({
      setup() {
        mutation = useExploreItemLikeMutation()
        return () => null
      },
    }),
    { global: { plugins: [i18n, [VueQueryPlugin, { queryClient }]] } },
  )

  return { queryClient, mutation }
}

describe('useExploreItemLikeMutation', () => {
  beforeEach(() => {
    likeExploreItem.mockReset()
    unlikeExploreItem.mockReset()
    showToast.mockReset()
  })

  it('applies the server-confirmed saved state to list and detail caches', async () => {
    likeExploreItem.mockResolvedValue({ saved: true })
    const { queryClient, mutation } = setup()
    const eventListKey = ['explore', 'events', 'list', { page: 0 }]
    const eventDetailKey = ['explore', 'events', 'detail', '42', 'en']
    const placeListKey = ['explore', 'places', 'list', { page: 0 }]
    queryClient.setQueryData(eventListKey, {
      content: [
        { itemId: 42, saved: false },
        { itemId: 7, saved: true },
      ],
    })
    queryClient.setQueryData(eventDetailKey, { eventId: 42, saved: false })
    queryClient.setQueryData(placeListKey, { content: [{ itemId: 42, saved: false }] })

    mutation.mutate({ itemId: 42, saved: true })
    await flushPromises()

    expect(likeExploreItem).toHaveBeenCalledWith(42)
    expect(queryClient.getQueryData(eventListKey)).toEqual({
      content: [
        { itemId: 42, saved: true },
        { itemId: 7, saved: true },
      ],
    })
    expect(queryClient.getQueryData(eventDetailKey)).toEqual({ eventId: 42, saved: true })
    expect(queryClient.getQueryData(placeListKey)).toEqual({
      content: [{ itemId: 42, saved: true }],
    })
  })

  it('invalidates saved-only lists because their membership changes', async () => {
    unlikeExploreItem.mockResolvedValue({ saved: false })
    const { queryClient, mutation } = setup()
    const savedOnlyKey = ['explore', 'places', 'list', { savedOnly: true }]
    const normalKey = ['explore', 'places', 'list', { page: 0 }]
    queryClient.setQueryData(savedOnlyKey, { content: [{ itemId: 42, saved: true }] })
    queryClient.setQueryData(normalKey, { content: [{ itemId: 42, saved: true }] })

    mutation.mutate({ itemId: 42, saved: false })
    await flushPromises()

    expect(unlikeExploreItem).toHaveBeenCalledWith(42)
    expect(queryClient.getQueryState(savedOnlyKey)?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(normalKey)?.isInvalidated).toBe(false)
  })

  it('leaves caches untouched and shows a toast when the request fails', async () => {
    likeExploreItem.mockRejectedValue(new Error('network'))
    const { queryClient, mutation } = setup()
    const eventListKey = ['explore', 'events', 'list', { page: 0 }]
    queryClient.setQueryData(eventListKey, { content: [{ itemId: 42, saved: false }] })

    mutation.mutate({ itemId: 42, saved: true })
    await flushPromises()

    expect(queryClient.getQueryData(eventListKey)).toEqual({
      content: [{ itemId: 42, saved: false }],
    })
    expect(showToast).toHaveBeenCalledWith('Could not update your saved list. Please try again.')
  })

  it('does not show a toast when the request succeeds', async () => {
    likeExploreItem.mockResolvedValue({ saved: true })
    const { mutation } = setup()

    mutation.mutate({ itemId: 42, saved: true })
    await flushPromises()

    expect(showToast).not.toHaveBeenCalled()
  })
})
