import { describe, expect, it } from 'vitest'

import {
  hasStepOneErrors,
  toJourneyCreateInput,
  validateJourneyForm,
  type JourneyFormDraft,
} from '../journeyForm'

function validDraft(): JourneyFormDraft {
  return {
    title: 'Seoul and Busan',
    startDate: '2026-08-10',
    endDate: '2026-08-12',
    budgetAmount: 1500000,
    companionPreference: '2-4',
  }
}

describe('journeyForm', () => {
  it('accepts a complete form and leaves regions empty', () => {
    const draft = validDraft()

    expect(validateJourneyForm(draft)).toEqual({})
    expect(toJourneyCreateInput(draft).regions).toEqual([])
  })

  it('rejects an end date before the start date', () => {
    const draft = validDraft()
    draft.endDate = '2026-08-09'

    const errors = validateJourneyForm(draft)

    expect(errors.endDate).toBe('journey.create.validation.dateOrder')
    expect(hasStepOneErrors(errors)).toBe(true)
  })

  it('keeps every optional field absent without inventing values', () => {
    const draft = validDraft()
    draft.budgetAmount = null
    draft.companionPreference = null

    expect(toJourneyCreateInput(draft)).toMatchObject({
      budgetAmount: null,
      companionPreference: null,
      regions: [],
    })
  })
})
