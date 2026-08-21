import { ArrowRight, CheckCircle2, Landmark, LockKeyhole, Repeat2, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'

export function LandingPage() {
  return (
    <div className="landing">
      <header className="landing-nav">
        <Link className="brand" to="/"><span className="brand-mark"><Landmark size={22} /></span>MiniLedger</Link>
        <div><Link className="text-link" to="/login">Đăng nhập</Link><Link className="button compact" to="/register">Dùng thử demo</Link></div>
      </header>
      <main>
        <section className="hero-section">
          <div className="eyebrow">DOUBLE-ENTRY LEDGER · PUBLIC DEMO</div>
          <h1>Chuyển tiền rõ ràng.<br /><em>Mọi đồng đều cân bằng.</em></h1>
          <p>Một ví điện tử mô phỏng được xây trên sổ cái kép, giao dịch ACID và idempotency</p>
          <div className="hero-actions"><Link className="button" to="/register">Tạo ví demo <ArrowRight size={18} /></Link><a className="button ghost" href="#how">Cách hoạt động</a></div>
          <div className="trust-row"><span><CheckCircle2 size={16} /> HTTPS & session bảo mật</span><span><CheckCircle2 size={16} /> Không lưu token trong trình duyệt</span><span><CheckCircle2 size={16} /> Dữ liệu chỉ dùng để demo</span></div>
        </section>
        <section id="how" className="feature-grid">
          <article><span><Repeat2 /></span><h2>Sổ cái kép</h2><p>Mỗi giao dịch sinh đúng một bút toán nợ và một bút toán có, giúp tổng tiền luôn được đối soát.</p></article>
          <article><span><LockKeyhole /></span><h2>Chống gửi trùng</h2><p>Idempotency key đảm bả  o thao tác thử lại không vô tình cộng hoặc chuyển tiền hai lần.</p></article>
          <article><span><ShieldCheck /></span><h2>Quyền sở hữu</h2><p>Mọi tài khoản và giao dịch được giới hạn theo phiên đăng nhập ở phía máy chủ.</p></article>
        </section>
      </main>
      <footer>MiniLedger là dự án mô phỏng kỹ thuật. Không sử dụng để lưu trữ hoặc xử lý tiền thật.</footer>
    </div>
  )
}
