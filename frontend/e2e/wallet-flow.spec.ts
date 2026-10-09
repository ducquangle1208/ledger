import { expect, test as base, type Page } from '@playwright/test'
import type { Account, MoneyMovement } from '../src/api/types.ts'

type Wallet = { page: Page; accountNumber: string }
const test = base.extend<{ wallets: { sender: Wallet; receiver: Wallet } }>({
  wallets: async ({ browser, baseURL }, use) => {
    const senderContext = await browser.newContext({ baseURL })
    const receiverContext = await browser.newContext({ baseURL })
    try {
      const sender = await createWallet(await senderContext.newPage(), 'sender')
      const receiver = await createWallet(await receiverContext.newPage(), 'receiver')
      await use({ sender, receiver })
    } finally {
      await senderContext.close()
      await receiverContext.close()
    }
  },
})

async function createWallet(page: Page, prefix: string): Promise<Wallet> {
  const username = `e2e-${prefix}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
  await page.goto('/register')
  await page.getByLabel('Tên người dùng', { exact: true }).fill(username)
  await page.getByLabel('Email', { exact: true }).fill(`${username}@example.com`)
  await page.getByLabel('Mật khẩu', { exact: true }).fill('Secret123!')
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click()
  await expect(page).toHaveURL(/\/app$/)
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click()
  await expect(page.locator('.balance-card')).toBeVisible()
  const accountNumber = await page.locator('.account-meta code').innerText()
  expect(accountNumber).toMatch(/^ML\d{10}$/)
  await expect(page.locator('.balance-card strong')).toHaveText('0 ₫')
  return { page, accountNumber }
}

async function claimDemoFunds(page: Page) {
  await page.getByRole('navigation').getByRole('link', { name: 'Nhận tiền demo' }).click()
  await page.getByRole('button', { name: 'Nhận tiền demo', exact: true }).click()
  await expect(page.locator('.success-message')).toContainText('Đã cộng 100.000')
  await expect(page.locator('.claim-complete')).toContainText('Đã nhận hôm nay')
}

async function fillTransfer(page: Page, recipient: string, amount: string, description: string) {
  await page.goto('/app/transfer')
  await page.getByLabel('Từ tài khoản').selectOption({ index: 1 })
  await page.getByLabel('Số tài khoản người nhận').fill(recipient)
  await page.getByLabel('Số tiền (VND)').fill(amount)
  await page.getByLabel(/Lời nhắn/).fill(description)
}

async function reviewTransfer(page: Page, recipient: string) {
  await page.getByRole('button', { name: 'Tiếp tục' }).click()
  await expect(page.getByRole('heading', { name: 'Kiểm tra giao dịch' })).toBeVisible()
  await expect(page.locator('.review-card')).toContainText(`•••• ${recipient.slice(-4)}`)
}

async function dashboardBalance(page: Page, expected: string) {
  await page.goto('/app')
  await expect(page.locator('.balance-card strong')).toHaveText(`${expected} ₫`)
}

async function account(page: Page): Promise<Account> {
  const response = await page.request.get('/api/v1/accounts')
  expect(response.status()).toBe(200)
  const accounts: Account[] = await response.json()
  expect(accounts).toHaveLength(1)
  return accounts[0]
}

test('two users transfer once, see exact balances and balanced receipt entries', async ({ wallets }) => {
  const { sender, receiver } = wallets
  await claimDemoFunds(sender.page)
  await dashboardBalance(sender.page, '100.000')
  await fillTransfer(sender.page, receiver.accountNumber, '25000', 'E2E payment')
  await reviewTransfer(sender.page, receiver.accountNumber)
  await sender.page.getByRole('button', { name: 'Xác nhận chuyển tiền' }).click()
  await expect(sender.page.getByText('GIAO DỊCH HOÀN TẤT')).toBeVisible()
  await expect(sender.page.locator('.receipt-card')).toContainText('75.000')
  await sender.page.getByRole('link', { name: 'Xem biên nhận' }).click()
  await expect(sender.page.getByRole('heading', { name: 'Bút toán kép' })).toBeVisible()
  const entries = sender.page.locator('.ledger-entries > div')
  await expect(entries).toHaveCount(2)
  await expect(entries.filter({ hasText: 'DEBIT' })).toContainText('Số dư sau: 75.000')
  await expect(entries.filter({ hasText: 'CREDIT' })).toContainText('Số dư sau: 25.000')
  await expect(entries.locator('strong')).toHaveText(['25.000 ₫', '25.000 ₫'])
  await dashboardBalance(sender.page, '75.000')
  await expect(sender.page.locator('.transaction-row')).toHaveCount(2)
  await expect(sender.page.locator('.transaction-row').filter({ hasText: 'E2E payment' })).toHaveCount(1)
  await dashboardBalance(receiver.page, '25.000')
  await expect(receiver.page.locator('.transaction-row')).toHaveCount(1)
  await receiver.page.getByRole('link').filter({ hasText: 'E2E payment' }).click()
  await expect(receiver.page.getByText('BIÊN NHẬN DEMO', { exact: true })).toBeVisible()
  await expect(receiver.page.locator('.receipt-status')).toContainText('25.000')
})

test('retry after a committed transfer loses its response replays without charging twice', async ({ wallets }) => {
  const { sender, receiver } = wallets
  await claimDemoFunds(sender.page)
  await fillTransfer(sender.page, receiver.accountNumber, '25000', 'E2E lost response')
  await reviewTransfer(sender.page, receiver.accountNumber)
  const attempts: { key: string | undefined; payload: string | null; status: number; body: MoneyMovement }[] = []
  await sender.page.route('**/api/v1/transfers', async route => {
    // Let the real API commit first, then drop only the first browser response.
    const response = await route.fetch({ maxRetries: 0 })
    attempts.push({
      key: route.request().headers()['idempotency-key'],
      payload: route.request().postData(), status: response.status(), body: await response.json(),
    })
    if (attempts.length === 1) await route.abort('failed')
    else await route.fulfill({ response })
  })
  await sender.page.getByRole('button', { name: 'Xác nhận chuyển tiền' }).click()
  await expect(sender.page.locator('.form-error')).toHaveText('Không thể hoàn tất giao dịch')
  expect(attempts).toHaveLength(1)
  expect(attempts[0].status).toBe(201)
  expect(attempts[0].body.replayed).toBe(false)
  expect(Number((await account(sender.page)).balance)).toBe(75000)
  expect(Number((await account(receiver.page)).balance)).toBe(25000)
  await sender.page.getByRole('button', { name: 'Xác nhận chuyển tiền' }).click()
  await expect(sender.page.getByText('GIAO DỊCH HOÀN TẤT')).toBeVisible()
  expect(attempts).toHaveLength(2)
  expect(attempts[0].key).toBeTruthy()
  expect(attempts[1].key).toBe(attempts[0].key)
  expect(attempts[1].payload).toBe(attempts[0].payload)
  expect(attempts[1].status).toBe(200)
  expect(attempts[1].body.replayed).toBe(true)
  expect(attempts[1].body.transactionId).toBe(attempts[0].body.transactionId)
  await dashboardBalance(sender.page, '75.000')
  await expect(sender.page.locator('.transaction-row')).toHaveCount(2)
  await expect(sender.page.locator('.transaction-row').filter({ hasText: 'E2E lost response' })).toHaveCount(1)
  await dashboardBalance(receiver.page, '25.000')
  await expect(receiver.page.locator('.transaction-row')).toHaveCount(1)
})

test('invalid amount, unknown recipient and insufficient funds show errors without changing balances', async ({ wallets }) => {
  const { sender, receiver } = wallets
  await fillTransfer(sender.page, 'ML0000000000', '0', 'E2E rejected payment')
  await sender.page.getByRole('button', { name: 'Tiếp tục' }).click()
  await expect(sender.page.getByText('Số tiền phải lớn hơn 0')).toBeVisible()
  await sender.page.getByLabel('Số tiền (VND)').fill('10')
  await sender.page.getByRole('button', { name: 'Tiếp tục' }).click()
  await expect(sender.page.getByText('Không tìm thấy tài khoản người nhận')).toBeVisible()
  await sender.page.getByLabel('Số tài khoản người nhận').fill(receiver.accountNumber)
  await reviewTransfer(sender.page, receiver.accountNumber)
  const failure = sender.page.waitForResponse(response => response.url().endsWith('/api/v1/transfers')
    && response.request().method() === 'POST')
  await sender.page.getByRole('button', { name: 'Xác nhận chuyển tiền' }).click()
  const response = await failure
  expect(response.status()).toBe(409)
  expect((await response.json()).code).toBe('INSUFFICIENT_FUNDS')
  await expect(sender.page.locator('.form-error')).toContainText('does not have enough funds')
  await expect(sender.page.getByRole('heading', { name: 'Kiểm tra giao dịch' })).toBeVisible()
  await dashboardBalance(sender.page, '0')
  await expect(sender.page.getByText('Chưa có giao dịch. Nhận tiền demo để bắt đầu.')).toBeVisible()
  await dashboardBalance(receiver.page, '0')
  await expect(receiver.page.getByText('Chưa có giao dịch. Nhận tiền demo để bắt đầu.')).toBeVisible()
})

test('authentication and daily faucet quota are enforced in the browser and API', async ({ page, request }) => {
  await page.goto('/app')
  await expect(page).toHaveURL(/\/login$/)
  const unauthenticated = await request.get('/api/v1/accounts')
  expect(unauthenticated.status()).toBe(401)
  expect((await unauthenticated.json()).code).toBe('UNAUTHORIZED')
  await createWallet(page, 'quota')
  await claimDemoFunds(page)
  await page.reload()
  await expect(page.locator('.claim-complete')).toContainText('Đã nhận hôm nay')
  await expect(page.getByRole('button', { name: 'Nhận tiền demo', exact: true })).toHaveCount(0)
  const walletAccount = await account(page)
  const csrf = await page.request.get('/api/v1/auth/csrf')
  expect(csrf.status()).toBe(200)
  const { token, headerName } = await csrf.json()
  expect(token).toBeTruthy()
  const second = await page.request.post('/api/v1/faucet/claims', {
    headers: { 'Idempotency-Key': `e2e-second-${Date.now()}`, [headerName]: token },
    data: { accountId: walletAccount.id },
  })
  expect(second.status()).toBe(409)
  expect((await second.json()).code).toBe('FAUCET_LIMIT_REACHED')
  await dashboardBalance(page, '100.000')
  await expect(page.locator('.transaction-row')).toHaveCount(1)
  await page.getByRole('button', { name: 'Đăng xuất' }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect((await page.request.get('/api/v1/accounts')).status()).toBe(401)
  await page.goto('/app')
  await expect(page).toHaveURL(/\/login$/)
})
