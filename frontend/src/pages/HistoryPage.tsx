import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { ArrowDownLeft, ArrowLeft, ArrowUpRight } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { formatDate, formatMoney } from '../lib/format'

export function HistoryPage() {
  const accountId = Number(useParams().accountId)
  const account = useQuery({ queryKey: ['account', accountId], queryFn: () => api.account(accountId) })
  const history = useInfiniteQuery({
    queryKey: ['history', accountId, 'all'],
    queryFn: ({ pageParam }) => api.history(accountId, pageParam, 20),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.nextCursor,
  })
  const items = history.data?.pages.flatMap((page) => page.items) ?? []

  return <div className="page-container">
    <Link className="text-button back" to="/app"><ArrowLeft size={17} /> Tổng quan</Link>
    <header className="page-heading"><div><div className="eyebrow">LỊCH SỬ</div><h1>Giao dịch tài khoản</h1><p>{account.data?.accountNumber} · {account.data && formatMoney(account.data.balance, account.data.currency)}</p></div></header>
    <section className="section-card"><div className="transaction-list">
      {items.map((item) => <Link key={`${item.transactionId}-${item.createdAt}`} to={`/app/transactions/${item.transactionId}`} className="transaction-row">
        <span className={`transaction-icon ${item.direction.toLowerCase()}`}>{item.direction === 'IN' ? <ArrowDownLeft /> : <ArrowUpRight />}</span>
        <div><strong>{item.description || item.type}</strong><small>{formatDate(item.createdAt)} · {item.counterpartyAccountNumber}</small></div>
        <div className={`transaction-amount ${item.direction.toLowerCase()}`}>{item.direction === 'IN' ? '+' : '−'}{formatMoney(item.amount, item.currency)}<small>{item.status}</small></div>
      </Link>)}
      {history.isLoading && <p>Đang tải lịch sử…</p>}
      {!history.isLoading && items.length === 0 && <div className="list-empty">Chưa có giao dịch.</div>}
    </div>{history.hasNextPage && <button className="button ghost dark full" onClick={() => history.fetchNextPage()} disabled={history.isFetchingNextPage}>{history.isFetchingNextPage ? 'Đang tải…' : 'Tải thêm'}</button>}</section>
  </div>
}
