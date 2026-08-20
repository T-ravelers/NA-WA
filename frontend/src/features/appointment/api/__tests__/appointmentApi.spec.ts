import { describe, expect, it, vi } from 'vitest'

import { httpClient } from '@/shared/api/httpClient'

import {
  cancelAppointmentParticipation,
  confirmAppointmentAttendance,
  fetchAppointments,
  fetchAppointmentMembers,
  fetchMyAppointmentParticipation,
  fetchMyOngoingAppointments,
  joinAppointment,
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

  it('joins an appointment and reads the current participation', async () => {
    const member = {
      appointmentMemberId: 11,
      memberId: 5,
      displayName: 'Alex Kim',
      profileImageUrl: null,
      preferredLanguage: 'en' as const,
      membershipStatus: 'PENDING' as const,
      attendanceStatus: 'PENDING' as const,
      isHost: false,
    }
    const get = vi.spyOn(httpClient, 'get').mockResolvedValue({
      data: {
        joined: true,
        appointmentMemberId: 11,
        membershipStatus: 'PENDING',
        attendanceStatus: 'PENDING',
        host: false,
      },
    })
    const post = vi.spyOn(httpClient, 'post').mockResolvedValue({ data: member })

    await expect(joinAppointment(7)).resolves.toEqual(member)
    await expect(fetchMyAppointmentParticipation(7)).resolves.toMatchObject({ joined: true })

    expect(post).toHaveBeenCalledWith('/api/v1/appointments/7/members')
    expect(get).toHaveBeenCalledWith('/api/v1/appointments/7/members/me')
    get.mockRestore()
    post.mockRestore()
  })

  it('cancels participation and confirms attendance through the appointment endpoints', async () => {
    const deleteRequest = vi.spyOn(httpClient, 'delete').mockResolvedValue({ data: undefined })
    const patch = vi.spyOn(httpClient, 'patch').mockResolvedValue({ data: undefined })

    await cancelAppointmentParticipation(7)
    await confirmAppointmentAttendance(7, {
      members: [{ memberId: 5, attendanceStatus: 'ATTENDED' }],
    })

    expect(deleteRequest).toHaveBeenCalledWith('/api/v1/appointments/7/members/me')
    expect(patch).toHaveBeenCalledWith('/api/v1/appointments/7/attendance', {
      members: [{ memberId: 5, attendanceStatus: 'ATTENDED' }],
    })
    deleteRequest.mockRestore()
    patch.mockRestore()
  })

  it('requests my ongoing appointments and defaults a missing list to empty', async () => {
    const appointment = {
      appointmentId: 42,
      appointmentName: 'Seoul Night Tour',
      tripId: 9,
      meetingPlace: 'Gwanghwamun Square',
      activityStartAt: '2026-08-10T18:00:00',
      activityEndAt: '2026-08-12T22:00:00',
    }
    const get = vi.spyOn(httpClient, 'get').mockResolvedValue({ data: [appointment] })

    await expect(fetchMyOngoingAppointments()).resolves.toEqual([appointment])
    expect(get).toHaveBeenCalledWith('/api/v1/appointments/me')

    get.mockResolvedValue({ data: undefined })
    await expect(fetchMyOngoingAppointments()).resolves.toEqual([])
    get.mockRestore()
  })
})
