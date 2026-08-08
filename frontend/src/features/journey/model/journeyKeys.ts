export const journeyKeys = {
  all: ['journeys'] as const,
  list: () => [...journeyKeys.all, 'list'] as const,
} as const
