import { useState } from 'react'
import { ShieldCheck, Lock, FileCheck, Server, Activity, Radio } from 'lucide-react'
import { api } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { SiemConfig } from '../lib/types'
import { Card, ErrorBanner, Spinner } from '../components/ui'
import { formatRelative } from '../lib/format'

// Pursued, not yet earned — never render these as if they were held certifications.
// Update only when the actual third-party audit/attestation exists.
const CERTS_IN_PROGRESS = ['SOC 2 Type II', 'ISO 27001', 'GDPR', 'HIPAA-ready']

const CONTROLS = [
  {
    icon: Lock,
    title: 'Argon2id credentials',
    body: 'Passwords hashed with the OWASP-recommended, memory-hard algorithm. Login is timing-safe against account enumeration.',
  },
  {
    icon: FileCheck,
    title: 'Tamper-evident audit log',
    body: 'Every action is hash-chained to the previous record. Altering history is detectable — verify it yourself on the Audit page.',
  },
  {
    icon: Server,
    title: 'Multi-tenant isolation',
    body: 'Enforced at the ORM layer: one customer can never read another\'s data, even on an application bug.',
  },
  {
    icon: Activity,
    title: 'Durable, resumable transfers',
    body: 'Transfers run as durable workflows that survive process crashes and auto-retry — no silently lost jobs.',
  },
]

export default function Trust() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Trust Center</h1>
        <p className="text-sm text-muted">Why regulated enterprises trust CloudFuze with critical files.</p>
      </div>

      <div className="rounded-xl bg-brand-header p-6 text-white">
        <div className="flex items-center gap-3">
          <ShieldCheck size={28} />
          <div>
            <div className="text-lg font-semibold">No reported breaches to date</div>
            <div className="text-sm text-white/70">
              MOVEit (2023) and GoAnywhere (2023) show why MFT products are a standing target.
              CloudFuze is built security-first from day one — this is a track record to keep, not a
              guarantee.
            </div>
          </div>
        </div>
        <div className="mt-4">
          <div className="text-xs font-medium uppercase tracking-wide text-white/60">
            Compliance roadmap — in progress, not yet certified
          </div>
          <div className="mt-2 flex flex-wrap gap-2">
            {CERTS_IN_PROGRESS.map((c) => (
              <span key={c} className="rounded-full border border-white/30 bg-white/10 px-3 py-1 text-xs font-medium text-white/85">
                {c}
              </span>
            ))}
          </div>
          <p className="mt-2 text-xs text-white/60">
            Ask for current audit status before relying on any of these in a procurement decision.
          </p>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {CONTROLS.map(({ icon: Icon, title, body }) => (
          <Card key={title}>
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-lightblue text-brand">
                <Icon size={20} />
              </div>
              <div>
                <div className="font-semibold">{title}</div>
                <p className="mt-1 text-sm text-muted">{body}</p>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <SiemPanel />
    </div>
  )
}

function SiemPanel() {
  const { data, refresh } = usePoll<SiemConfig>(() => api('/api/siem'), 4000)
  const [type, setType] = useState('HTTP')
  const [target, setTarget] = useState('')
  const [token, setToken] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function save(enabled: boolean) {
    setBusy(true); setErr(null)
    try {
      await api('/api/siem', {
        method: 'POST',
        body: { type, target: target || data?.target, token, enabled },
      })
      setToken('')
      refresh()
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <div className="flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-lightblue text-brand">
          <Radio size={20} />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2 font-semibold">
            Security event stream → SIEM
            {data?.enabled && (
              <span className="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-xs text-success dark:bg-green-900/40 dark:text-green-300">
                <Activity size={11} /> streaming
              </span>
            )}
          </div>
          <p className="mt-1 text-sm text-muted">
            Forward every audit event to Splunk / Sentinel (HTTP/HEC or syslog-CEF), at-least-once
            with a durable cursor. <span className="font-medium">63% of MFT systems lack this.</span>
          </p>

          <ErrorBanner message={err} />

          {data?.configured && (
            <div className="mt-3 rounded-lg bg-nearwhite p-3 text-sm dark:bg-slate-900">
              <div className="flex justify-between"><span className="text-muted">Destination</span><span className="font-medium">{data.type} · {data.target}</span></div>
              <div className="flex justify-between"><span className="text-muted">Events forwarded (cursor)</span><span className="font-medium">up to seq {data.lastForwardedSeq}</span></div>
              <div className="flex justify-between"><span className="text-muted">Last status</span><span className="font-medium">{data.lastStatus || '—'}</span></div>
              <div className="flex justify-between"><span className="text-muted">Last forwarded</span><span className="font-medium">{formatRelative(data.lastForwardedAt)}</span></div>
            </div>
          )}

          <div className="mt-3 grid grid-cols-3 gap-2">
            <select className="input" value={type} onChange={(e) => setType(e.target.value)}>
              <option value="HTTP">HTTP / HEC</option>
              <option value="SYSLOG_TCP">Syslog TCP (CEF)</option>
            </select>
            <input className="input col-span-2" placeholder={data?.target || 'https://splunk:8088/... or host:514'} value={target} onChange={(e) => setTarget(e.target.value)} />
            <input className="input col-span-3" type="password" placeholder={data?.hasToken ? 'token stored — leave blank to keep' : 'bearer token (optional)'} value={token} onChange={(e) => setToken(e.target.value)} />
          </div>
          <div className="mt-3 flex gap-2">
            <button className="btn-primary" disabled={busy} onClick={() => save(true)}>
              {busy ? <Spinner className="text-white" /> : data?.enabled ? 'Update & keep streaming' : 'Enable streaming'}
            </button>
            {data?.enabled && (
              <button className="btn-ghost" disabled={busy} onClick={() => save(false)}>Pause</button>
            )}
          </div>
        </div>
      </div>
    </Card>
  )
}
