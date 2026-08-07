import type { CompanionPreference, JourneyCreateInput } from '../api/journeyApi'

const MAX_TITLE_LENGTH = 100
const MAX_BUDGET = 999_999_999_999_999

export interface JourneyFormDraft {
  title: string
  startDate: string
  endDate: string
  budgetAmount: number | null
  companionPreference: CompanionPreference | null
}

export interface JourneyFormErrors {
  title?: string
  startDate?: string
  endDate?: string
  budgetAmount?: string
}

export function validateJourneyForm(draft: JourneyFormDraft): JourneyFormErrors {
  const errors: JourneyFormErrors = {}
  const title = draft.title.trim()

  if (title === '') {
    errors.title = 'journey.create.validation.titleRequired'
  } else if (title.length > MAX_TITLE_LENGTH) {
    errors.title = 'journey.create.validation.titleTooLong'
  }

  if (draft.startDate === '') {
    errors.startDate = 'journey.create.validation.startDateRequired'
  }

  if (draft.endDate === '') {
    errors.endDate = 'journey.create.validation.endDateRequired'
  } else if (draft.startDate !== '' && draft.startDate > draft.endDate) {
    errors.endDate = 'journey.create.validation.dateOrder'
  }

  if (draft.budgetAmount !== null && (draft.budgetAmount < 0 || draft.budgetAmount > MAX_BUDGET)) {
    errors.budgetAmount = 'journey.create.validation.budgetInvalid'
  }

  return errors
}

export function hasStepOneErrors(errors: JourneyFormErrors): boolean {
  return (
    errors.title !== undefined || errors.startDate !== undefined || errors.endDate !== undefined
  )
}

export function toJourneyCreateInput(draft: JourneyFormDraft): JourneyCreateInput {
  return {
    title: draft.title.trim(),
    startDate: draft.startDate,
    endDate: draft.endDate,
    budgetAmount: draft.budgetAmount,
    companionPreference: draft.companionPreference,
    regions: [],
  }
}
