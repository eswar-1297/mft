import { useState } from 'react'
import { ShieldCheck, ShieldOff, KeyRound } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Card, ErrorBanner, Spinner } from '../components/ui'

interface EnrollResponse {
  secret: string
  otpauthUri: string
}

export default function Security() {
  const { user } = useAuth()
  const [enroll, setEnroll] = useState<EnrollResponse | null>(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [enabled, setEnabled] = useState(user?.mfaEnabled ?? false)

  async function startEnroll() {
    setBusy(true); setErr(null)
    try {
      setEnroll(await api<EnrollResponse>('/api/auth/mfa/enroll', { method: 'POST' }))
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed')
    } finally {
      setBusy(false)
    }
  }

  async function confirm() {
    setBusy(true); setErr(null)
    try {
      await api('/api/auth/mfa/confirm', { method: 'POST', body: { code } })
      setEnabled(true)
      setEnroll(null)
      setCode('')
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Invalid code')
    } finally {
      setBusy(false)
    }
  }

  async function disable() {
    const c = prompt('Enter a current authenticator code to disable MFA:')
    if (!c) return
    setBusy(true); setErr(null)
    try {
      await api('/api/auth/mfa/disable', { method: 'POST', body: { code: c } })
      setEnabled(false)
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Security</h1>
        <p className="text-sm text-muted">Protect your account with multi-factor authentication.</p>
      </div>

      <ErrorBanner message={err} />

      <Card>
        <div className="flex items-start gap-3">
          <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${enabled ? 'bg-green-100 text-success' : 'bg-lightblue text-brand'}`}>
            {enabled ? <ShieldCheck size={20} /> : <KeyRound size={20} />}
          </div>
          <div className="flex-1">
            <div className="font-semibold">
              Two-factor authentication (TOTP)
              <span className={`ml-2 rounded-full px-2 py-0.5 text-xs ${enabled ? 'bg-green-100 text-success' : 'bg-slate-100 text-muted'}`}>
                {enabled ? 'Enabled' : 'Disabled'}
              </span>
            </div>
            <p className="mt-1 text-sm text-muted">
              Use Google Authenticator, Authy, or 1Password. A code is required at every login.
            </p>

            {!enabled && !enroll && (
              <button className="btn-primary mt-4" onClick={startEnroll} disabled={busy}>
                {busy ? <Spinner className="text-white" /> : 'Enable MFA'}
              </button>
            )}

            {enroll && (
              <div className="mt-4 space-y-3">
                <div className="rounded-lg bg-nearwhite p-3 text-sm dark:bg-slate-900">
                  <div className="text-muted">Add this secret to your authenticator app:</div>
                  <div className="mt-1 break-all font-mono font-medium">{enroll.secret}</div>
                </div>
                <div>
                  <label className="label">Enter the 6-digit code to confirm</label>
                  <input
                    className="input tracking-[0.4em]"
                    inputMode="numeric"
                    maxLength={6}
                    placeholder="123456"
                    value={code}
                    onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                  />
                </div>
                <button className="btn-primary" onClick={confirm} disabled={busy || code.length !== 6}>
                  {busy ? <Spinner className="text-white" /> : 'Confirm & enable'}
                </button>
              </div>
            )}

            {enabled && (
              <button className="btn-ghost mt-4 text-danger" onClick={disable} disabled={busy}>
                <ShieldOff size={16} /> Disable MFA
              </button>
            )}
          </div>
        </div>
      </Card>
    </div>
  )
}
