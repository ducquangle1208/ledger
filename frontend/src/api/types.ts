export type User = {
  id: number
  username: string
  email: string
  createdAt: string
}

export type Account = {
  id: number
  userId: number
  accountNumber: string
  currency: string
  balance: string
  status: 'ACTIVE' | 'FROZEN' | 'CLOSED'
  createdAt: string
  updatedAt: string
}

export type AccountPreview = {
  accountNumber: string
  maskedAccountNumber: string
  currency: string
  status: string
}

export type FaucetStatus = {
  available: boolean
  amount: string
  claimedOn?: string
  nextAvailableAt?: string
}

export type MoneyMovement = {
  transactionId: number
  referenceCode: string
  type: 'DEPOSIT' | 'TRANSFER'
  status: string
  amount: string
  currency: string
  description?: string
  debitAccountId: number
  debitBalanceAfter: string
  creditAccountId: number
  creditBalanceAfter: string
  createdAt: string
  completedAt: string
  replayed: boolean
}

export type HistoryItem = {
  transactionId: number
  type: string
  status: string
  direction: 'IN' | 'OUT'
  amount: string
  currency: string
  description?: string
  balanceAfter: string
  counterpartyAccountNumber: string
  createdAt: string
}

export type HistoryPage = {
  items: HistoryItem[]
  nextCursor?: string
  hasMore: boolean
}

export type TransactionEntry = {
  id: number
  accountId: number
  entryType: 'DEBIT' | 'CREDIT'
  amount: string
  balanceAfter: string
  createdAt: string
}

export type TransactionDetail = {
  id: number
  referenceCode: string
  type: string
  status: string
  amount: string
  currency: string
  description?: string
  createdAt: string
  completedAt: string
  entries: TransactionEntry[]
}

export type ApiErrorBody = {
  status: number
  code: string
  message: string
  path: string
  details?: Record<string, unknown>
}
