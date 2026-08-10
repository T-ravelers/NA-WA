import type { Category } from '@/shared/ui/category'

export interface ExploreActivityOption {
  id: number
  labelKey: string
}

export interface ExploreSectorOption {
  id: number
  labelKey: string
  category: Category
  activities: ExploreActivityOption[]
}

/**
 * Explore taxonomy seed shared by the Event and Place filters. IDs match
 * EXPLORE_TAXONOMY_MOCK_DATA_LOCAL_ONLY.sql.
 *
 * Keep this collection stable. Changing these IDs changes the meaning of every
 * saved Explore filter URL and of the sectorIds/activityIds the API receives.
 */
export const EVENT_SECTOR_OPTIONS: ExploreSectorOption[] = [
  {
    id: 1,
    labelKey: 'explore.categories.food',
    category: 'food',
    activities: [
      { id: 101, labelKey: 'explore.activities.cafeDessert' },
      { id: 102, labelKey: 'explore.activities.foodFestival' },
      { id: 103, labelKey: 'explore.activities.restaurant' },
      { id: 104, labelKey: 'explore.activities.barLiquor' },
      { id: 105, labelKey: 'explore.activities.snack' },
      { id: 106, labelKey: 'explore.activities.other' },
    ],
  },
  {
    id: 2,
    labelKey: 'explore.categories.beauty',
    category: 'beauty',
    activities: [
      { id: 201, labelKey: 'explore.activities.kBeauty' },
      { id: 202, labelKey: 'explore.activities.makeupCosmetics' },
      { id: 203, labelKey: 'explore.activities.perfume' },
      { id: 204, labelKey: 'explore.activities.beautyDevice' },
      { id: 205, labelKey: 'explore.activities.other' },
    ],
  },
  {
    id: 3,
    labelKey: 'explore.categories.shopping',
    category: 'shopping',
    activities: [
      { id: 301, labelKey: 'explore.activities.fashion' },
      { id: 302, labelKey: 'explore.activities.lifestyleHomeware' },
      { id: 303, labelKey: 'explore.activities.bookStationery' },
      { id: 304, labelKey: 'explore.activities.kidsFamily' },
      { id: 305, labelKey: 'explore.activities.travelHobby' },
      { id: 306, labelKey: 'explore.activities.sportsLeisure' },
      { id: 307, labelKey: 'explore.activities.digitalTech' },
      { id: 308, labelKey: 'explore.activities.artIllust' },
      { id: 309, labelKey: 'explore.activities.jewelryWatch' },
      { id: 310, labelKey: 'explore.activities.pets' },
      { id: 311, labelKey: 'explore.activities.healthFitness' },
      { id: 312, labelKey: 'explore.activities.other' },
    ],
  },
  {
    id: 4,
    labelKey: 'explore.categories.show',
    category: 'show',
    activities: [
      { id: 401, labelKey: 'explore.activities.characterGoods' },
      { id: 402, labelKey: 'explore.activities.festival' },
      { id: 403, labelKey: 'explore.activities.animeWebtoon' },
      { id: 404, labelKey: 'explore.activities.fanMeeting' },
      { id: 405, labelKey: 'explore.activities.game' },
      { id: 406, labelKey: 'explore.activities.exhibition' },
      { id: 407, labelKey: 'explore.activities.performance' },
      { id: 408, labelKey: 'explore.activities.expoFair' },
      { id: 409, labelKey: 'explore.activities.heritageFestival' },
      { id: 410, labelKey: 'explore.activities.filmDrama' },
      { id: 411, labelKey: 'explore.activities.traditionalPerformance' },
      { id: 412, labelKey: 'explore.activities.concert' },
      { id: 413, labelKey: 'explore.activities.natureFestival' },
      { id: 414, labelKey: 'explore.activities.playTheater' },
      { id: 415, labelKey: 'explore.activities.classicalConcert' },
      { id: 416, labelKey: 'explore.activities.creator' },
      { id: 417, labelKey: 'explore.activities.musical' },
      { id: 418, labelKey: 'explore.activities.opera' },
      { id: 419, labelKey: 'explore.activities.dance' },
      { id: 420, labelKey: 'explore.activities.nonVerbal' },
      { id: 421, labelKey: 'explore.activities.sports' },
      { id: 422, labelKey: 'explore.activities.other' },
    ],
  },
]

export const EVENT_ACTIVITY_OPTIONS = EVENT_SECTOR_OPTIONS.flatMap((sector) => sector.activities)

/**
 * Place reuses the Event taxonomy until the Place taxonomy contract is agreed.
 *
 * The Place backend (#138) filters on the same sector and activity tables as
 * Event, and the local seed only contains these IDs. Declaring a separate Place
 * ID range here would send activity IDs that no row matches.
 */
export const PLACE_SECTOR_OPTIONS = EVENT_SECTOR_OPTIONS
export const PLACE_ACTIVITY_OPTIONS = EVENT_ACTIVITY_OPTIONS
