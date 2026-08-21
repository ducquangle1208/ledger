import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2, Copy, Landmark } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { formatDate, formatMoney } from '../lib/format'

export function TransactionPage() {
  const id = Number(useParams().transactionId)
  const transaction = useQuery({ queryKey: ['transaction', id], queryFn: () => api.transaction(id) })
  const data = transaction.data
  if (transaction.isLoading) return <div className="page-container narrow"><div className="loading-card">Đang tải biên nhận…</div></div>
  if (!data) return <div className="page-container narrow"><div className="error-card">Không thể tải giao dịch này.</div></div>

  return <div className="page-container narrow">
    <Link className="text-button back" to="/app"><ArrowLeft size={17} /> Tổng quan</Link>
    <section className="receipt-detail">
      <div className="receipt-brand"><span className="brand-mark"><Landmark /></span>MiniLedger <small>BIÊN NHẬN DEMO</small></div>
      <div className="receipt-status"><span><CheckCircle2 /></span><div className="eyebrow">{data.status}</div><h1>{formatMoney(data.amount, data.currency)}</h1><p>{data.description || data.type}</p></div>
      <dl>
        <div><dt>Mã giao dịch</dt><dd>#{data.id}</dd></div>
        <div><dt>Reference code</dt><dd className="mono">{data.referenceCode}<button className="copy-button" aria-label="Sao chép" onClick={() => navigator.clipboard.writeText(data.referenceCode)}><Copy size={15} /></button></dd></div>
        <div><dt>Loại giao dịch</dt><dd>{data.type}</dd></div>
        <div><dt>Hoàn tất lúc</dt><dd>{formatDate(data.completedAt)}</dd></div>
      </dl>
      <div className="ledger-entries"><h2>Bút toán kép</h2>{data.entries.map((entry) => <div key={entry.id}><span className={entry.entryType.toLowerCase()}>{entry.entryType}</span><code>Tài khoản #{entry.accountId}</code><strong>{formatMoney(entry.amount, data.currency)}</strong><small>Số dư sau: {formatMoney(entry.balanceAfter, data.currency)}</small></div>)}</div>
      <div className="receipt-foot">Biên nhận này phản ánh dữ liệu mô phỏng trong sổ cái MiniLedger, không phải chứng từ thanh toán thật.</div>
    </section>
  </div>
}
