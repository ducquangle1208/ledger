import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, CheckCircle2, Send, ShieldCheck } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { api, ApiError } from '../api/client'
import type { AccountPreview } from '../api/types'
import { formatMoney, newIdempotencyKey } from '../lib/format'

const schema = z.object({
  fromAccountId: z.number().positive(),
  recipientAccountNumber: z.string().min(3, 'Nhập số tài khoản người nhận').max(20),
  amount: z.string().regex(/^\d{1,16}(\.\d{1,2})?$/, 'Số tiền chưa hợp lệ').refine((v) => Number(v) >= 1, 'Số tiền phải lớn hơn 0'),
  description: z.string().max(500).optional(),
})
type Input = z.infer<typeof schema>

export function TransferPage() {
  const queryClient = useQueryClient()
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: api.accounts })
  const [step, setStep] = useState<'form' | 'review' | 'done'>('form')
  const [preview, setPreview] = useState<AccountPreview | null>(null)
  const [intentKey, setIntentKey] = useState(() => newIdempotencyKey('transfer'))
  const form = useForm<Input>({ resolver: zodResolver(schema), defaultValues: { description: '' } })
  // React Hook Form's subscription API is intentionally left outside compiler memoization.
  // eslint-disable-next-line react-hooks/incompatible-library
  const values = form.watch()
  const source = useMemo(() => accounts.data?.find((item) => item.id === Number(values.fromAccountId)), [accounts.data, values.fromAccountId])

  const transfer = useMutation({
    mutationFn: () => api.transfer({ ...form.getValues(), fromAccountId: Number(form.getValues().fromAccountId) }, intentKey),
    onSuccess: async () => {
      setStep('done')
      await Promise.all([queryClient.invalidateQueries({ queryKey: ['accounts'] }), queryClient.invalidateQueries({ queryKey: ['history'] })])
    },
  })

  const review = form.handleSubmit(async (input) => {
    try {
      const account = await api.previewAccount(input.recipientAccountNumber)
      if (account.status !== 'ACTIVE') throw new Error('Tài khoản người nhận không hoạt động')
      setPreview(account)
      setStep('review')
    } catch (error) {
      form.setError('recipientAccountNumber', { message: error instanceof ApiError ? 'Không tìm thấy tài khoản người nhận' : (error as Error).message })
    }
  })

  if (step === 'done' && transfer.data) return <div className="page-container narrow"><section className="receipt-card"><span><CheckCircle2 /></span><div className="eyebrow">GIAO DỊCH HOÀN TẤT</div><h1>{formatMoney(transfer.data.amount, transfer.data.currency)}</h1><p>Đã chuyển đến {preview?.maskedAccountNumber}</p><dl><div><dt>Mã giao dịch</dt><dd>#{transfer.data.transactionId}</dd></div><div><dt>Trạng thái</dt><dd>{transfer.data.status}</dd></div><div><dt>Số dư còn lại</dt><dd>{formatMoney(transfer.data.debitBalanceAfter, transfer.data.currency)}</dd></div></dl><Link className="button full" to={`/app/transactions/${transfer.data.transactionId}`}>Xem biên nhận</Link><button className="text-button" onClick={() => { form.reset(); setPreview(null); setIntentKey(newIdempotencyKey('transfer')); setStep('form') }}>Thực hiện giao dịch khác</button></section></div>

  return (
    <div className="page-container narrow">
      <header className="page-heading"><div><div className="eyebrow">CHUYỂN TIỀN</div><h1>{step === 'form' ? 'Bạn muốn gửi cho ai?' : 'Kiểm tra giao dịch'}</h1><p>{step === 'form' ? 'Chỉ chuyển đến tài khoản MiniLedger đang hoạt động.' : 'Hãy chắc chắn thông tin bên dưới là chính xác.'}</p></div></header>
      {step === 'form' ? <form className="section-card transfer-form" onSubmit={review}>
        <label>Từ tài khoản<select {...form.register('fromAccountId', { valueAsNumber: true })} defaultValue=""><option value="" disabled>Chọn tài khoản nguồn</option>{accounts.data?.map((account) => <option key={account.id} value={account.id}>{account.accountNumber} · {formatMoney(account.balance, account.currency)}</option>)}</select><span>{form.formState.errors.fromAccountId?.message}</span></label>
        <label>Số tài khoản người nhận<input placeholder="ML000000000001" autoComplete="off" {...form.register('recipientAccountNumber')} /><span>{form.formState.errors.recipientAccountNumber?.message}</span></label>
        <label>Số tiền (VND)<div className="money-input"><input inputMode="decimal" placeholder="0" {...form.register('amount')} /><span>₫</span></div><span>{form.formState.errors.amount?.message}</span>{source && <small>Số dư khả dụng: {formatMoney(source.balance, source.currency)}</small>}</label>
        <label>Lời nhắn <small>Không bắt buộc</small><textarea rows={3} placeholder="Nội dung chuyển tiền" {...form.register('description')} /></label>
        <button className="button full" type="submit">Tiếp tục <ArrowRight size={18} /></button>
        <div className="secure-note"><ShieldCheck /> Giao dịch được bảo vệ bằng idempotency và ghi sổ nguyên tử.</div>
      </form> : <section className="section-card review-card">
        <button className="text-button back" onClick={() => setStep('form')}><ArrowLeft size={17} /> Sửa thông tin</button>
        <div className="review-amount"><span>Số tiền chuyển</span><strong>{formatMoney(values.amount || '0', 'VND')}</strong></div>
        <dl><div><dt>Từ</dt><dd>{source?.accountNumber}<small>{source && formatMoney(source.balance, source.currency)}</small></dd></div><div><dt>Đến</dt><dd>{preview?.maskedAccountNumber}<small>{preview?.currency} · {preview?.status}</small></dd></div><div><dt>Nội dung</dt><dd>{values.description || 'Không có'}</dd></div></dl>
        {transfer.isError && <div className="form-error">{transfer.error instanceof ApiError ? transfer.error.message : 'Không thể hoàn tất giao dịch'}</div>}
        <button className="button full" onClick={() => transfer.mutate()} disabled={transfer.isPending}><Send size={18} /> {transfer.isPending ? 'Đang chuyển tiền…' : 'Xác nhận chuyển tiền'}</button>
      </section>}
    </div>
  )
}
