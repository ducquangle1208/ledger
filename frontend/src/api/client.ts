import type {
  Account,
  AccountPreview,
  ApiErrorBody,
  FaucetStatus,
  HistoryPage,
  MoneyMovement,
  TransactionDetail,
  User,
} from './types'

export class ApiError extends Error {
  readonly body: ApiErrorBody

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.body = body
  }
}

let csrfToken: string | null = null
let csrfHeader = 'X-XSRF-TOKEN'

async function ensureCsrf() {
  if (csrfToken) return
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  if (!response.ok) throw new Error('Unable to initialize security token')
  const body = (await response.json()) as { token: string; headerName: string }
  csrfToken = body.token
  csrfHeader = body.headerName
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method?.toUpperCase() ?? 'GET'
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) await ensureCsrf()
  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(!['GET', 'HEAD', 'OPTIONS'].includes(method) && csrfToken
        ? { [csrfHeader]: csrfToken }
        : {}),
      ...init.headers,
    },
  })

  if (response.status === 204) return undefined as T
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(body as ApiErrorBody)
  return body as T
}

export const api = {
  csrf: ensureCsrf,
  me: () => request<User>('/api/v1/auth/me'),
  register: (input: { username: string; email: string; password: string }) =>
    request<User>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify(input) }),
  login: (input: { login: string; password: string }) =>
    request<User>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify(input) }),
  logout: () => request<void>('/api/v1/auth/logout', { method: 'POST' }),
  accounts: () => request<Account[]>('/api/v1/accounts'),
  createAccount: (currency = 'VND') =>
    request<Account>('/api/v1/accounts', { method: 'POST', body: JSON.stringify({ currency }) }),
  account: (id: number) => request<Account>(`/api/v1/accounts/${id}`),
  previewAccount: (number: string) =>
    request<AccountPreview>(`/api/v1/accounts/by-number/${encodeURIComponent(number)}`),
  faucetStatus: () => request<FaucetStatus>('/api/v1/faucet/status'),
  claimFaucet: (accountId: number, key: string) =>
    request<MoneyMovement>('/api/v1/faucet/claims', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify({ accountId }),
    }),
  transfer: (
    input: { fromAccountId: number; recipientAccountNumber: string; amount: string; description?: string },
    key: string,
  ) =>
    request<MoneyMovement>('/api/v1/transfers', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify(input),
    }),
  history: (accountId: number, cursor?: string, limit = 20) => {
    const params = new URLSearchParams({ limit: String(limit) })
    if (cursor) params.set('cursor', cursor)
    return request<HistoryPage>(`/api/v1/accounts/${accountId}/transactions?${params}`)
  },
  transaction: (id: number) => request<TransactionDetail>(`/api/v1/transactions/${id}`),
}

export function resetCsrf() {
  csrfToken = null
}
