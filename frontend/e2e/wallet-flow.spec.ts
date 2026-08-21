import { expect, test, type Browser, type Page } from '@playwright/test'

async function register(page: Page, prefix: string) {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
  const username = `${prefix}-${suffix}`
  await page.goto('/register')
  await page.getByLabel('Tên người dùng').fill(username)
  await page.getByLabel('Email').fill(`${username}@example.com`)
  await page.getByLabel('Mật khẩu').fill('Secret123!')
  await page.getByRole('button', { name: 'Tạo tài khoản' }).click()
  await expect(page).toHaveURL(/\/app$/)
  return username
}

async function createAccount(page: Page) {
  await page.getByRole('button', { name: /Tạo tài khoản/ }).click()
  await expect(page.locator('.balance-card')).toBeVisible()
  const accountNumber = await page.locator('.account-meta code').innerText()
  expect(accountNumber).toMatch(/^ML\d{10}$/)
  return accountNumber
}

async function claimDemoFunds(page: Page) {
  await page.getByRole('link', { name: /Nhận tiền demo/ }).first().click()
  await page.getByRole('button', { name: 'Nhận tiền demo' }).click()
  await expect(page.getByText(/Đã cộng 100\.000/)).toBeVisible()
}

async function newRegisteredWallet(browser: Browser, prefix: string) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await register(page, prefix)
  const accountNumber = await createAccount(page)
  return { context, page, accountNumber }
}

test('two users can fund, transfer, and inspect a receipt', async ({ browser }) => {
  const sender = await newRegisteredWallet(browser, 'e2e-sender')
  const receiver = await newRegisteredWallet(browser, 'e2e-receiver')

  try {
    await claimDemoFunds(sender.page)
    await sender.page.goto('/app/transfer')
    await sender.page.getByLabel('Từ tài khoản').selectOption({ index: 1 })
    await sender.page.getByLabel('Số tài khoản người nhận').fill(receiver.accountNumber)
    await sender.page.getByLabel('Số tiền (VND)').fill('25000')
    await sender.page.getByLabel(/Lời nhắn/).fill('Playwright demo payment')
    await sender.page.getByRole('button', { name: /Tiếp tục/ }).click()
    await expect(sender.page.getByText(receiver.accountNumber.slice(-4))).toBeVisible()
    await sender.page.getByRole('button', { name: /Xác nhận chuyển tiền/ }).click()
    await expect(sender.page.getByText('GIAO DỊCH HOÀN TẤT')).toBeVisible()
    await sender.page.getByRole('link', { name: 'Xem biên nhận' }).click()
    await expect(sender.page.getByText('Playwright demo payment')).toBeVisible()
    await expect(sender.page.getByText('Bút toán kép')).toBeVisible()

    await receiver.page.goto('/app')
    await expect(receiver.page.getByText('Playwright demo payment')).toBeVisible()
    await receiver.page.getByText('Playwright demo payment').click()
    await expect(receiver.page.getByText('BIÊN NHẬN DEMO')).toBeVisible()
  } finally {
    await sender.context.close()
    await receiver.context.close()
  }
})

test('unauthenticated API and repeated faucet claims are rejected', async ({ browser, request }) => {
  const unauthenticated = await request.get('/api/v1/accounts')
  expect(unauthenticated.status()).toBe(401)
  expect((await unauthenticated.json()).code).toBe('UNAUTHORIZED')

  const wallet = await newRegisteredWallet(browser, 'e2e-quota')
  try {
    await claimDemoFunds(wallet.page)
    const account = await wallet.page.request.get('/api/v1/accounts')
    expect(account.status()).toBe(200)
    const [body] = await account.json()
    const csrf = await wallet.page.request.get('/api/v1/auth/csrf')
    expect(csrf.status()).toBe(200)
    const { token, headerName } = await csrf.json()
    expect(token).toBeTruthy()
    const second = await wallet.page.request.post('/api/v1/faucet/claims', {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': `e2e-second-${Date.now()}`,
        [headerName]: token,
      },
      data: { accountId: body.id },
    })
    expect(second.status()).toBe(409)
    expect((await second.json()).code).toBe('FAUCET_LIMIT_REACHED')
  } finally {
    await wallet.context.close()
  }
})
