import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDownLeft, ArrowRight, ArrowUpRight, Plus, RefreshCw, Send, Sparkles, Wallet } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../app/auth-context'
import { formatDate, formatMoney } from '../lib/format'

export function DashboardPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: api.accounts })
  const primary = accounts.data?.[0]
  const history = useQuery({
    queryKey: ['history', primary?.id],
    queryFn: () => api.history(primary!.id, undefined, 6),
    enabled: Boolean(primary),
  })
  const createAccount = useMutation({
    mutationFn: () => api.createAccount(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  })

  return (
    <div className="page-container">
      <header className="page-heading"><div><div className="eyebrow">TỔNG QUAN</div><h1>Xin chào, {user?.username}.</h1><p>Đây là tình hình ví demo của bạn hôm nay.</p></div><button className="icon-button" onClick={() => queryClient.invalidateQueries()} aria-label="Làm mới"><RefreshCw size={18} /></button></header>
      {accounts.isLoading && <div className="loading-card">Đang tải tài khoản…</div>}
      {accounts.isError && <div className="error-card">Không thể tải tài khoản. Hãy thử lại.</div>}
      {!accounts.isLoading && !primary && (
        <section className="empty-state"><span><Wallet /></span><h2>Tạo tài khoản VND đầu tiên</h2><p>Mỗi tài khoản demo bắt đầu từ 0 ₫ và có thể nhận tiền mô phỏng hằng ngày.</p><button className="button" onClick={() => createAccount.mutate()} disabled={createAccount.isPending}><Plus size={18} /> Tạo tài khoản</button></section>
      )}
      {primary && <>
        <section className="balance-card">
          <div><span>Số dư khả dụng</span><strong>{formatMoney(primary.balance, primary.currency)}</strong><small>Cập nhật {formatDate(primary.updatedAt)}</small></div>
          <div className="account-meta"><span>{primary.currency}</span><code>{primary.accountNumber}</code><small>{primary.status === 'ACTIVE' ? '● Đang hoạt động' : primary.status}</small></div>
        </section>
        <section className="quick-actions">
          <Link to="/app/transfer"><span><Send /></span><strong>Chuyển tiền</strong><small>Đến tài khoản MiniLedger</small><ArrowRight size={18} /></Link>
          <Link to="/app/faucet"><span><Sparkles /></span><strong>Nhận tiền demo</strong><small>Hạn mức một lần mỗi ngày</small><ArrowRight size={18} /></Link>
          {accounts.data && accounts.data.length < 3 && <button onClick={() => createAccount.mutate()}><span><Plus /></span><strong>Tài khoản mới</strong><small>Tối đa 3 tài khoản demo</small><ArrowRight size={18} /></button>}
        </section>
        <section className="section-card">
          <div className="section-title"><div><h2>Giao dịch gần đây</h2><p>Lịch sử được lấy theo cursor, mới nhất trước.</p></div>{history.data?.hasMore && <Link to={`/app/accounts/${primary.id}/history`}>Xem tất cả</Link>}</div>
          <div className="transaction-list">
            {history.isLoading && <p>Đang tải giao dịch…</p>}
            {history.data?.items.length === 0 && <div className="list-empty">Chưa có giao dịch. Nhận tiền demo để bắt đầu.</div>}
            {history.data?.items.map((item) => <Link key={item.transactionId} to={`/app/transactions/${item.transactionId}`} className="transaction-row">
              <span className={`transaction-icon ${item.direction.toLowerCase()}`}>{item.direction === 'IN' ? <ArrowDownLeft /> : <ArrowUpRight />}</span>
              <div><strong>{item.description || (item.direction === 'IN' ? 'Tiền vào' : 'Chuyển tiền')}</strong><small>{formatDate(item.createdAt)} · {item.counterpartyAccountNumber}</small></div>
              <div className={`transaction-amount ${item.direction.toLowerCase()}`}>{item.direction === 'IN' ? '+' : '−'}{formatMoney(item.amount, item.currency)}<small>Số dư {formatMoney(item.balanceAfter, item.currency)}</small></div>
            </Link>)}
          </div>
        </section>
      </>}
    </div>
  )
}
