import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Clock3 } from 'lucide-react'
import { useState } from 'react'
import { api, ApiError } from '../api/client'
import { formatMoney, newIdempotencyKey } from '../lib/format'

export function FaucetPage() {
  const queryClient = useQueryClient()
  const [intentKey] = useState(() => newIdempotencyKey('faucet'))
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: api.accounts })
  const status = useQuery({ queryKey: ['faucet-status'], queryFn: api.faucetStatus })
  const claim = useMutation({
    mutationFn: () => api.claimFaucet(accounts.data![0].id, intentKey),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['accounts'] }),
        queryClient.invalidateQueries({ queryKey: ['faucet-status'] }),
        queryClient.invalidateQueries({ queryKey: ['history'] }),
      ])
    },
  })
  const available = status.data?.available && Boolean(accounts.data?.length)

  return (
    <div className="page-container narrow">
      <header className="page-heading"><div><div className="eyebrow">DEMO FAUCET</div><h1>Nhận tiền mô phỏng</h1><p>Một khoản nhỏ để bạn khám phá luồng chuyển tiền — hoàn toàn không có giá trị thực.</p></div></header>
      <section className="faucet-card">
        <div className="eyebrow">HẠN MỨC MỖI NGÀY</div>
        <strong>{formatMoney(status.data?.amount ?? '100000', 'VND')}</strong>
        <p>Được ghi có vào tài khoản VND chính. Máy chủ đảm bảo mỗi người chỉ nhận một lần trong một ngày UTC.</p>
        {status.isLoading ? <button className="button full" disabled>Đang kiểm tra…</button> : available ? (
          <button className="button full" onClick={() => claim.mutate()} disabled={claim.isPending}>{claim.isPending ? 'Đang ghi vào sổ cái…' : 'Nhận tiền demo'}</button>
        ) : (
          <div className="claim-complete"><CheckCircle2 /> Đã nhận hôm nay{status.data?.nextAvailableAt && <small>Thử lại sau {new Date(status.data.nextAvailableAt).toLocaleString('vi-VN')}</small>}</div>
        )}
        {claim.isSuccess && <div className="success-message"><CheckCircle2 /> Đã cộng {formatMoney(claim.data.amount, claim.data.currency)}. Giao dịch #{claim.data.transactionId}</div>}
        {claim.isError && <div className="form-error">{claim.error instanceof ApiError ? claim.error.message : 'Không thể nhận tiền demo'}</div>}
      </section>
      <section className="info-strip"><Clock3 /></section>
    </div>
  )
}
