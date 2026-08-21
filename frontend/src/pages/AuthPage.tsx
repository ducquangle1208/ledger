import { zodResolver } from '@hookform/resolvers/zod'
import { Landmark } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { ApiError, api } from '../api/client'
import { useAuth } from '../app/auth-context'

const loginSchema = z.object({ login: z.string().min(1, 'Vui lòng nhập tài khoản'), password: z.string().min(8, 'Tối thiểu 8 ký tự') })
const registerSchema = z.object({ username: z.string().min(3, 'Tối thiểu 3 ký tự').max(50), email: z.string().email('Email chưa hợp lệ'), password: z.string().min(8, 'Tối thiểu 8 ký tự').max(100) })
type LoginInput = z.infer<typeof loginSchema>
type RegisterInput = z.infer<typeof registerSchema>

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  return mode === 'login' ? <LoginForm /> : <RegisterForm />
}

function Story({ login }: { login: boolean }) {
  return <section className="auth-story"><Link className="brand inverse" to="/"><span className="brand-mark"><Landmark size={22} /></span>MiniLedger</Link><div><div className="eyebrow light">DEMO WALLET</div><h1>{login ? 'Chào mừng trở lại.' : 'Một ví nhỏ.\nMột sổ cái vững.'}</h1><p>Trải nghiệm cách giao dịch được bảo vệ bởi ACID, pessimistic locking và double-entry bookkeeping.</p></div><small>Tiền và tài khoản trong hệ thống chỉ phục vụ mục đích học tập.</small></section>
}

function LoginForm() {
  const navigate = useNavigate(); const { setUser } = useAuth()
  const form = useForm<LoginInput>({ resolver: zodResolver(loginSchema) })
  const submit = form.handleSubmit(async (values) => { try { setUser(await api.login(values)); navigate('/app') } catch (error) { form.setError('root', { message: error instanceof ApiError ? error.message : 'Không thể kết nối máy chủ' }) } })
  return <div className="auth-layout"><Story login /><main className="auth-panel"><form onSubmit={submit}><div className="mobile-brand"><Landmark /> MiniLedger</div><h2>Đăng nhập</h2><p>Tiếp tục với tài khoản của bạn.</p><label>Tên người dùng hoặc email<input autoComplete="username" {...form.register('login')} /><span>{form.formState.errors.login?.message}</span></label><label>Mật khẩu<input type="password" autoComplete="current-password" {...form.register('password')} /><span>{form.formState.errors.password?.message}</span></label>{form.formState.errors.root && <div className="form-error" role="alert">{form.formState.errors.root.message}</div>}<button className="button full" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? 'Đang xử lý…' : 'Đăng nhập'}</button><div className="auth-switch">Chưa có tài khoản? <Link to="/register">Đăng ký</Link></div></form></main></div>
}

function RegisterForm() {
  const navigate = useNavigate(); const { setUser } = useAuth()
  const form = useForm<RegisterInput>({ resolver: zodResolver(registerSchema) })
  const submit = form.handleSubmit(async (values) => { try { setUser(await api.register(values)); navigate('/app') } catch (error) { form.setError('root', { message: error instanceof ApiError ? error.message : 'Không thể kết nối máy chủ' }) } })
  return <div className="auth-layout"><Story login={false} /><main className="auth-panel"><form onSubmit={submit}><div className="mobile-brand"><Landmark /> MiniLedger</div><h2>Tạo ví demo</h2><p>Bắt đầu trong chưa đầy một phút.</p><label>Tên người dùng<input autoComplete="username" {...form.register('username')} /><span>{form.formState.errors.username?.message}</span></label><label>Email<input type="email" autoComplete="email" {...form.register('email')} /><span>{form.formState.errors.email?.message}</span></label><label>Mật khẩu<input type="password" autoComplete="new-password" {...form.register('password')} /><span>{form.formState.errors.password?.message}</span></label>{form.formState.errors.root && <div className="form-error" role="alert">{form.formState.errors.root.message}</div>}<button className="button full" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? 'Đang xử lý…' : 'Tạo tài khoản'}</button><div className="auth-switch">Đã có tài khoản? <Link to="/login">Đăng nhập</Link></div></form></main></div>
}
