import { describe, expect, it, vi } from 'vitest'

import { httpClient } from '@/shared/api/httpClient'

import {
  fetchAppointments,
  fetchAppointmentMembers,
  type AppointmentListResponse,
} from '../appointmentApi'

describe('appointmentApi', () => {
  it('requests appointment lists with item and language filters', async () => {
    const response: AppointmentListResponse = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    }
    const get = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: response })

    await expect(
      fetchAppointments({ itemId: 42, itemType: 'EVENT', language: 'en', page: 0, size: 20 }),
    ).resolves.toEqual(response)

    expect(get).toHaveBeenCalledWith('/api/v1/appointments', {
      params: { itemId: 42, itemType: 'EVENT', language: 'en', page: 0, size: 20 },
    })
    get.mockRestore()
  })

  it('uses the appointment member endpoint', async () => {
    const get = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: [] })

    await expect(fetchAppointmentMembers(7)).resolves.toEqual([])
    expect(get).toHaveBeenCalledWith('/api/v1/appointments/7/members')
    get.mockRestore()
  })
})
