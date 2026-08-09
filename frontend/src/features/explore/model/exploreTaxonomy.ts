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
 * Local Explore taxonomy seed.
 *
 * IDs and the sector order mirror the operational_v9 handoff. The values are
 * kept locally until the taxonomy API is available; they are not a new
 * classification model.
 */
export const EVENT_SECTOR_OPTIONS: ExploreSectorOption[] = [
  {
    id: 1,
    labelKey: 'explore.categories.beauty',
    category: 'beauty',
    activities: [
      { id: 1, labelKey: 'explore.activities.makeupCosmetics' },
      { id: 2, labelKey: 'explore.activities.skincare' },
      { id: 3, labelKey: 'explore.activities.perfume' },
      { id: 4, labelKey: 'explore.activities.beautyDevice' },
      { id: 5, labelKey: 'explore.activities.haircare' },
      { id: 6, labelKey: 'explore.activities.nail' },
      { id: 7, labelKey: 'explore.activities.spaSauna' },
      { id: 8, labelKey: 'explore.activities.aestheticClinic' },
    ],
  },
  {
    id: 2,
    labelKey: 'explore.categories.food',
    category: 'food',
    activities: [
      { id: 9, labelKey: 'explore.activities.cafeDessert' },
      { id: 10, labelKey: 'explore.activities.restaurant' },
      { id: 11, labelKey: 'explore.activities.touristRestaurant' },
      { id: 12, labelKey: 'explore.activities.streetFood' },
      { id: 13, labelKey: 'explore.activities.barLiquor' },
      { id: 14, labelKey: 'explore.activities.teaHouse' },
      { id: 15, labelKey: 'explore.activities.snack' },
      { id: 16, labelKey: 'explore.activities.foodFestival' },
    ],
  },
  {
    id: 3,
    labelKey: 'explore.categories.show',
    category: 'show',
    activities: [
      { id: 17, labelKey: 'explore.activities.characterGoods' },
      { id: 18, labelKey: 'explore.activities.animeWebtoon' },
      { id: 19, labelKey: 'explore.activities.fanMeeting' },
      { id: 20, labelKey: 'explore.activities.game' },
      { id: 21, labelKey: 'explore.activities.exhibition' },
      { id: 22, labelKey: 'explore.activities.festival' },
      { id: 23, labelKey: 'explore.activities.filmDrama' },
      { id: 24, labelKey: 'explore.activities.creator' },
      { id: 25, labelKey: 'explore.activities.performance' },
      { id: 26, labelKey: 'explore.activities.expoFair' },
      { id: 27, labelKey: 'explore.activities.heritageFestival' },
      { id: 28, labelKey: 'explore.activities.natureFestival' },
      { id: 29, labelKey: 'explore.activities.traditionalPerformance' },
      { id: 30, labelKey: 'explore.activities.concert' },
      { id: 31, labelKey: 'explore.activities.kPopConcert' },
      { id: 32, labelKey: 'explore.activities.classicalConcert' },
      { id: 33, labelKey: 'explore.activities.playTheater' },
      { id: 34, labelKey: 'explore.activities.musical' },
      { id: 35, labelKey: 'explore.activities.opera' },
      { id: 36, labelKey: 'explore.activities.dance' },
      { id: 37, labelKey: 'explore.activities.nonVerbal' },
    ],
  },
  {
    id: 4,
    labelKey: 'explore.categories.shopping',
    category: 'shopping',
    activities: [
      { id: 38, labelKey: 'explore.activities.fashion' },
      { id: 39, labelKey: 'explore.activities.apparel' },
      { id: 40, labelKey: 'explore.activities.bagsShoes' },
      { id: 41, labelKey: 'explore.activities.jewelryWatch' },
      { id: 42, labelKey: 'explore.activities.lifestyleHomeware' },
      { id: 43, labelKey: 'explore.activities.bookStationery' },
      { id: 44, labelKey: 'explore.activities.artIllust' },
      { id: 45, labelKey: 'explore.activities.digitalTech' },
      { id: 46, labelKey: 'explore.activities.kidsFamily' },
      { id: 47, labelKey: 'explore.activities.pets' },
      { id: 48, labelKey: 'explore.activities.sportsLeisure' },
      { id: 49, labelKey: 'explore.activities.travelHobby' },
      { id: 50, labelKey: 'explore.activities.healthFitness' },
      { id: 51, labelKey: 'explore.activities.departmentStore' },
      { id: 52, labelKey: 'explore.activities.traditionalMarket' },
      { id: 53, labelKey: 'explore.activities.shoppingMall' },
      { id: 54, labelKey: 'explore.activities.dutyFree' },
      { id: 55, labelKey: 'explore.activities.souvenirs' },
      { id: 56, labelKey: 'explore.activities.craftWorkshop' },
    ],
  },
]

export const EVENT_ACTIVITY_OPTIONS = EVENT_SECTOR_OPTIONS.flatMap((sector) => sector.activities)
