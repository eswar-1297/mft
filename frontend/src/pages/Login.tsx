import { useState } from 'react'
import { ShieldCheck } from 'lucide-react'
import { useAuth } from '../lib/auth'
import { ErrorBanner, Spinner } from '../components/ui'

export default function Login() {
  const { login } = useAuth()
  const [tenantSlug, setTenantSlug] = useState('demo')
  const [email, setEmail] = useState('admin@cloudfuze.com')
  const [password, setPassword] = useState('')
  const [otp, setOtp] = useState('')
  const [mfaRequired, setMfaRequired] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(tenantSlug.trim(), email.trim(), password, otp.trim() || undefined)
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Login failed'
      if (message === 'MFA_REQUIRED') {
        // Password was accepted; now prompt for the authenticator code.
        setMfaRequired(true)
        setError(null)
      } else {
        setError(message === 'Invalid MFA code' ? 'Invalid authenticator code' : message)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen">
      {/* Brand panel */}
      <div className="hidden w-1/2 flex-col justify-between bg-brand-header p-12 text-white lg:flex">
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/15 text-lg font-bold">
            C
          </div>
          <span className="font-semibold">CloudFuze MFT</span>
        </div>
        <div>
          <h1 className="text-3xl font-semibold leading-tight">
            Secure, provable file transfer for regulated enterprises.
          </h1>
          <p className="mt-4 max-w-md text-white/70">
            Real SHA-256-verified transfers, a tamper-evident audit log, and database-enforced
            tenant isolation — security built into the architecture, not bolted on.
          </p>
          <div className="mt-8 flex items-center gap-2 text-sm text-white/80">
            <ShieldCheck size={18} /> AES-256-GCM vault · Hash-chained audit · No reported breaches to date
          </div>
        </div>
        <div className="text-xs text-white/50">© CloudFuze, Inc.</div>
      </div>

      {/* Form */}
      <div className="flex w-full items-center justify-center p-8 lg:w-1/2">
        <form onSubmit={onSubmit} className="w-full max-w-sm space-y-4">
          <div>
            <h2 className="text-2xl font-semibold">Sign in</h2>
            <p className="mt-1 text-sm text-muted">Access your organization's transfer console.</p>
          </div>
          <ErrorBanner message={error} />
          <div>
            <label className="label">Organization</label>
            <input className="input" value={tenantSlug} onChange={(e) => setTenantSlug(e.target.value)} />
          </div>
          <div>
            <label className="label">Email</label>
            <input
              className="input"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="username"
            />
          </div>
          <div>
            <label className="label">Password</label>
            <input
              className="input"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              disabled={mfaRequired}
            />
          </div>
          {mfaRequired && (
            <div>
              <label className="label">Authenticator code</label>
              <input
                className="input tracking-[0.4em]"
                inputMode="numeric"
                maxLength={6}
                placeholder="123456"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                autoFocus
              />
              <p className="mt-1 text-xs text-muted">
                Enter the 6-digit code from your authenticator app.
              </p>
            </div>
          )}
          <button className="btn-primary w-full" type="submit" disabled={busy}>
            {busy ? <Spinner className="text-white" /> : mfaRequired ? 'Verify & sign in' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  )
}
