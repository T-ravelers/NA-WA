import { z } from 'zod'

const finiteNumberSchema = z.number().finite()
const nullableStringSchema = z.string().nullable()
const localDateStringSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/)
const ENTRY_TYPES = ['DEBIT', 'CREDIT'] as const

/** Jackson LocalDateTime without @JsonFormat: a finite component array of length 3..7. */
export const serverDateTimeSchema = z.union([
  z.string(),
  z.array(finiteNumberSchema).min(3).max(7),
  z.null(),
])

/** Jackson LocalDate: ISO yyyy-MM-dd when formatted as text, or a three-part array. */
export const serverLocalDateSchema = z.union([
  localDateStringSchema,
  z.array(finiteNumberSchema).length(3),
  z.null(),
])

const walletTransactionShape = {
  transferId: z.number().int().finite(),
  // transferType remains forward-compatible; entryType controls monetary direction and is strict.
  transferType: z.string(),
  entryType: z.enum(ENTRY_TYPES),
  amount: finiteNumberSchema,
  balanceAfter: finiteNumberSchema,
  createdAt: serverDateTimeSchema,
}

export const walletTransactionResponseSchema = z.object(walletTransactionShape).passthrough()

export const walletHomeResponseSchema = z
  .object({
    balance: finiteNumberSchema,
    availabilityStatus: z.string(),
    // The model normalizes a nullable transaction collection before a view dereference.
    recentTransactions: z.array(walletTransactionResponseSchema).nullable(),
  })
  .passthrough()

export const transactionAppliedFiltersSchema = z
  .object({
    // Enum-like filters remain strings so new backend values do not reject the whole response.
    type: nullableStringSchema,
    status: nullableStringSchema,
    from: serverLocalDateSchema,
    to: serverLocalDateSchema,
  })
  .passthrough()

export const transactionListResponseSchema = z
  .object({
    transactions: z.array(walletTransactionResponseSchema),
    nextCursor: nullableStringSchema,
    appliedFilters: transactionAppliedFiltersSchema,
  })
  .passthrough()

const transactionCounterpartyResponseSchema = z
  .object({
    type: z.string(),
    name: z.string(),
  })
  .passthrough()

const transactionReceiptResponseSchema = z
  .object({
    transactionNumber: nullableStringSchema,
    memo: nullableStringSchema,
    spendingCategory: nullableStringSchema,
  })
  .passthrough()

const transactionFxResponseSchema = z
  .object({
    sourceAmount: finiteNumberSchema.nullable(),
    sourceCurrency: nullableStringSchema,
    displayCurrency: nullableStringSchema,
    exchangeRate: finiteNumberSchema.nullable(),
    ratedAt: serverDateTimeSchema,
  })
  .passthrough()

export const transactionDetailResponseSchema = z
  .object({
    amount: finiteNumberSchema,
    occurredAt: serverDateTimeSchema,
    counterparty: transactionCounterpartyResponseSchema.nullable(),
    // Keep the status open for values added by the backend.
    status: z.string(),
    receipt: transactionReceiptResponseSchema.nullable(),
    transactionNumber: nullableStringSchema,
    fx: transactionFxResponseSchema.nullable(),
  })
  .passthrough()

export const walletHomeSchema = walletHomeResponseSchema
export const transactionListSchema = transactionListResponseSchema
export const transactionDetailSchema = transactionDetailResponseSchema
