import { useState } from 'react'
import { ShieldCheck, ShieldAlert, Search } from 'lucide-react'
import { api } from '../lib/api'
import { usePoll } from '../lib/usePoll'
import type { AuditEvent, ChainVerification, Page } from '../lib/types'
import { Card, EmptyState, ErrorBanner, Spinner } from '../components/ui'
import { formatDateTime } from '../lib/format'

export default function Audit() {
  const [filter, setFilter] = useState('')
  const query = filter ? `&action=${encodeURIComponent(filter)}` : ''
  const { data, error } = usePoll<Page<AuditEvent>>(
    () => api(`/api/audit?size=100${query}`),
    5000,
  )
  const [verification, setVerification] = useState<ChainVerification | null>(null)
  const [verifying, setVerifying] = useState(false)

  async function verify() {
    setVerifying(true)
    try {
      setVerification(await api<ChainVerification>('/api/audit/verify'))
    } catch (e) {
      setVerification({
        valid: false,
        recordCount: 0,
        brokenAtSeq: null,
        message: e instanceof Error ? e.message : 'Verification failed',
      })
    } finally {
      setVerifying(false)
    }
  }

  const events = data?.content ?? []

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Audit &amp; Compliance</h1>
        <p className="text-sm text-muted">
          Immutable, tamper-evident log. Every record is hash-chained to the previous one.
        </p>
      </div>

      <ErrorBanner message={error} />

      {/* Chain verification — the Trust Center's evidence, runnable on demand. */}
      <Card>
        <div className="flex items-center justify-between">
          <div>
            <h2 className="font-semibold">Tamper-evident chain</h2>
            <p className="text-sm text-muted">
              Recomputes every record's hash and confirms the chain is unbroken.
            </p>
          </div>
          <button className="btn-primary" onClick={verify} disabled={verifying}>
            {verifying ? <Spinner className="text-white" /> : 'Verify chain integrity'}
          </button>
        </div>
        {verification && (
          <div
            className={`mt-4 flex items-start gap-3 rounded-lg px-4 py-3 ${
              verification.valid
                ? 'bg-green-50 text-green-800 dark:bg-green-900/20 dark:text-green-300'
                : 'bg-red-50 text-danger dark:bg-red-900/20 dark:text-red-300'
            }`}
          >
            {verification.valid ? (
              <ShieldCheck className="mt-0.5 shrink-0" size={20} />
            ) : (
              <ShieldAlert className="mt-0.5 shrink-0" size={20} />
            )}
            <div>
              <div className="font-medium">{verification.message}</div>
              {verification.brokenAtSeq !== null && (
                <div className="text-sm">First tampered record: sequence #{verification.brokenAtSeq}</div>
              )}
            </div>
          </div>
        )}
      </Card>

      <Card>
        <div className="mb-3 flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-2.5 text-muted" size={16} />
            <input
              className="input pl-9"
              placeholder="Filter by action, e.g. transfer.completed"
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
            />
          </div>
        </div>
        {events.length === 0 ? (
          <EmptyState>No audit records match.</EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 text-left text-xs uppercase tracking-wide text-muted dark:border-slate-800">
                  <th className="pb-2 font-medium">#</th>
                  <th className="pb-2 font-medium">Action</th>
                  <th className="pb-2 font-medium">Actor</th>
                  <th className="pb-2 font-medium">When</th>
                  <th className="pb-2 font-medium">Hash</th>
                </tr>
              </thead>
              <tbody>
                {events.map((e) => (
                  <tr key={e.seq} className="border-b border-slate-50 dark:border-slate-800/50">
                    <td className="py-2.5 text-muted">{e.seq}</td>
                    <td className="py-2.5 font-medium">{e.action}</td>
                    <td className="py-2.5 text-muted">{e.actorEmail || 'system'}</td>
                    <td className="py-2.5 text-muted">{formatDateTime(e.occurredAt)}</td>
                    <td className="py-2.5 font-mono text-xs text-muted">{e.hash.slice(0, 12)}…</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
