import { z } from 'zod'

import { EVENT_KINDS, EVENT_STATUSES } from '../model/eventExplore'

const idSchema = z.number().int().finite()
const finiteNumberSchema = z.number().finite()
const nullableStringSchema = z.string().nullable()
const nullableDateSchema = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/)
  .nullable()

const eventActivityShape = {
  activityId: idSchema,
  activityCode: nullableStringSchema,
  activityName: nullableStringSchema,
  sectorId: idSchema.nullable(),
  sectorCode: nullableStringSchema,
  sectorName: nullableStringSchema,
  isPrimary: z.boolean().nullable(),
}

export const eventActivityResponseSchema = z.object(eventActivityShape).passthrough()

export const eventSummaryResponseSchema = z
  .object({
    itemId: idSchema,
    eventKind: z.enum(EVENT_KINDS),
    status: z.enum(EVENT_STATUSES),
    title: z.string(),
    subtitle: nullableStringSchema,
    thumbnailUrl: nullableStringSchema,
    region1: nullableStringSchema,
    region2: nullableStringSchema,
    region3: nullableStringSchema,
    latitude: finiteNumberSchema.nullable(),
    longitude: finiteNumberSchema.nullable(),
    startDate: nullableDateSchema,
    endDate: nullableDateSchema,
  })
  .passthrough()

export const eventListResponseSchema = z
  .object({
    content: z.array(eventSummaryResponseSchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().nonnegative(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
    hasNext: z.boolean(),
  })
  .passthrough()

export const eventDetailResponseSchema = z
  .object({
    eventId: idSchema,
    eventType: nullableStringSchema,
    eventKind: z.enum(EVENT_KINDS),
    title: z.string(),
    subtitle: nullableStringSchema,
    description: nullableStringSchema,
    programText: nullableStringSchema,
    thumbnailUrl: nullableStringSchema,
    imageUrls: z.unknown(),
    links: z.unknown(),
    reservationUrl: nullableStringSchema,
    preReservation: z.unknown(),
    status: z.enum(EVENT_STATUSES),
    isPermanent: z.boolean().nullable(),
    startDate: nullableDateSchema,
    endDate: nullableDateSchema,
    operatingHours: z.unknown(),
    openDays: z.unknown(),
    openWeekend: z.boolean().nullable(),
    opensLate: z.boolean().nullable(),
    venueName: nullableStringSchema,
    region1: nullableStringSchema,
    region2: nullableStringSchema,
    region3: nullableStringSchema,
    addressRoad: nullableStringSchema,
    latitude: finiteNumberSchema.nullable(),
    longitude: finiteNumberSchema.nullable(),
    hasPhotoZone: z.boolean().nullable(),
    isExperience: z.boolean().nullable(),
    ageLimit: nullableStringSchema,
    isFree: z.boolean().nullable(),
    priceText: nullableStringSchema,
    hasBenefit: z.boolean().nullable(),
    reservable: z.boolean().nullable(),
    contact: nullableStringSchema,
    organizer: nullableStringSchema,
    activities: z.array(eventActivityResponseSchema),
  })
  .passthrough()

const placeSummaryShape = {
  itemId: idSchema,
  name: z.string(),
  brand: nullableStringSchema,
  branch: nullableStringSchema,
  // 원천 값은 서버에서 정규화하지만 새 값이 추가돼도 검증에서 막지 않는다.
  placeKind: nullableStringSchema,
  thumbnailUrl: nullableStringSchema,
  imageUrls: z.unknown(),
  region1: nullableStringSchema,
  region2: nullableStringSchema,
  region3: nullableStringSchema,
  addressRoad: nullableStringSchema,
  addressDetail: nullableStringSchema,
  latitude: finiteNumberSchema.nullable(),
  longitude: finiteNumberSchema.nullable(),
  hasForeignLang: z.boolean().nullable().optional(),
  hasParking: z.boolean().nullable().optional(),
  reservable: z.boolean().nullable().optional(),
  takeoutAvailable: z.boolean().nullable().optional(),
  cardPaymentAvailable: z.boolean().nullable().optional(),
  smokeFree: z.boolean().nullable().optional(),
  kidFacility: z.boolean().nullable().optional(),
  hasRestroom: z.boolean().nullable().optional(),
  isActive: z.boolean().nullable(),
  viewCount: z.number().int().nonnegative(),
  favoriteCount: z.number().int().nonnegative(),
}

export const placeSummaryResponseSchema = z.object(placeSummaryShape).passthrough()

export const placeListResponseSchema = z
  .object({
    // Place API normalizes null content to [] before a view dereferences it.
    content: z.array(placeSummaryResponseSchema).nullable(),
    page: z.number().int().nonnegative(),
    size: z.number().int().nonnegative(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
    hasNext: z.boolean(),
  })
  .passthrough()

const placeActivityShape = {
  activityId: idSchema,
  activityCode: nullableStringSchema,
  activityName: nullableStringSchema,
  sectorId: idSchema.nullable(),
  sectorCode: nullableStringSchema,
  sectorName: nullableStringSchema,
  isPrimary: z.boolean().nullable(),
}

export const placeActivityResponseSchema = z.object(placeActivityShape).passthrough()

export const placeDetailResponseSchema = z
  .object({
    // Older API payloads used itemId while newer payloads expose placeId. The model
    // normalizer accepts the optional placeId and always has the required itemId.
    placeId: idSchema.nullable().optional(),
    ...placeSummaryShape,
    sourceUrl: nullableStringSchema,
    postalCode: nullableStringSchema,
    openingHours: z.unknown(),
    closedDays: z.unknown(),
    menuSummary: nullableStringSchema,
    tel: nullableStringSchema,
    activities: z.array(placeActivityResponseSchema).nullable(),
  })
  .passthrough()

// Short aliases make the feature API call sites readable without introducing a schema registry.
export const eventListSchema = eventListResponseSchema
export const eventDetailSchema = eventDetailResponseSchema
export const placeListSchema = placeListResponseSchema
export const placeDetailSchema = placeDetailResponseSchema
